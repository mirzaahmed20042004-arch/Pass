"""Voice Filter backend: landing page, APK download and the community preset API
consumed by the Android app (GET/POST /api/presets).

Security posture:
* strict pydantic validation + server-side sanitising of every text field
* optional bearer-style auth: set VOICEFILTER_TOKEN in the env and POST
  /api/presets requires a matching X-VoiceFilter-Token header
* per-IP sliding-window rate limits (global + stricter for writes)
* security headers on every response (nosniff, frame deny, referrer, CSP)
* no CORS wildcard, no eval, no dynamic code, bounded preset store
* HTTPS-ready: put this app behind TLS (the Android app ships a
  network-security-config that still allows cleartext for LAN/demo servers)
"""
import json
import os
import re
import time
from collections import defaultdict, deque

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATIC = os.path.join(ROOT, "backend", "static")
PRESETS_FILE = os.path.join(ROOT, "backend", "presets.json")
APK_DIR = os.path.join(ROOT, "apk")

API_TOKEN = os.environ.get("VOICEFILTER_TOKEN", "").strip()
RATE_GLOBAL = 120          # requests / minute / ip
RATE_WRITE_AUTHED = 30     # writes / minute / ip with valid token
RATE_WRITE_OPEN = 5        # writes / minute / ip without token
MAX_PRESETS = 100

SEED = [
    {"name": "Deep Commander", "author": "voicefilter", "pitch": 0.72, "strength": 1.0,
     "low_db": 6.0, "low_hz": 160, "high_db": -5.0, "high_hz": 3200, "gain_db": 0.0, "echo_ms": 0},
    {"name": "Radio Demon", "author": "voicefilter", "pitch": 0.88, "strength": 0.9,
     "low_db": 2.0, "low_hz": 240, "high_db": 3.0, "high_hz": 5000, "gain_db": 0.0, "echo_ms": 0},
    {"name": "Bubblegum Scout", "author": "voicefilter", "pitch": 1.42, "strength": 1.0,
     "low_db": -4.0, "low_hz": 250, "high_db": 5.0, "high_hz": 5200, "gain_db": 0.0, "echo_ms": 0},
    {"name": "Female Lead", "author": "voicefilter", "pitch": 1.30, "strength": 1.0,
     "low_db": -3.5, "low_hz": 220, "high_db": 4.5, "high_hz": 4500, "gain_db": 0.0, "echo_ms": 0},
    {"name": "Male Lead", "author": "voicefilter", "pitch": 0.78, "strength": 1.0,
     "low_db": 4.5, "low_hz": 180, "high_db": -4.0, "high_hz": 3500, "gain_db": 0.0, "echo_ms": 0},
    {"name": "Ghost Whisper", "author": "voicefilter", "pitch": 1.12, "strength": 0.75,
     "low_db": -6.0, "low_hz": 300, "high_db": 6.0, "high_hz": 6000, "gain_db": 2.0, "echo_ms": 220},
]

app = FastAPI(title="Voice Filter backend", version="2.0")
app.mount("/static", StaticFiles(directory=STATIC), name="static")

_hits = defaultdict(deque)


def _client_ip(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for", "")
    return fwd.split(",")[0].strip() if fwd else (request.client.host if request.client else "unknown")


def _allowed(ip: str, bucket: str, limit: int) -> bool:
    now = time.time()
    q = _hits[(ip, bucket)]
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= limit:
        return False
    q.append(now)
    return True


@app.middleware("http")
async def security(request: Request, call_next):
    ip = _client_ip(request)
    if not _allowed(ip, "global", RATE_GLOBAL):
        return JSONResponse({"detail": "rate limited"}, status_code=429)
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Content-Security-Policy"] = (
        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'")
    response.headers["Cache-Control"] = "no-store"
    return response


def load_presets():
    if not os.path.exists(PRESETS_FILE):
        save_presets(SEED)
        return list(SEED)
    try:
        with open(PRESETS_FILE) as f:
            data = json.load(f)
        return data if isinstance(data, list) else list(SEED)
    except (ValueError, OSError):
        return list(SEED)


def save_presets(items):
    tmp = PRESETS_FILE + ".tmp"
    with open(tmp, "w") as f:
        json.dump(items, f, indent=2)
    os.replace(tmp, PRESETS_FILE)


def clean_text(value: str, limit: int) -> str:
    value = re.sub(r"[\x00-\x1f\x7f<>\"'\\]", "", value or "")
    return value.strip()[:limit]


class PresetIn(BaseModel):
    name: str = Field(min_length=1, max_length=40)
    author: str = Field(default="anonymous", max_length=40)
    pitch: float = Field(ge=0.4, le=2.5)
    strength: float = Field(ge=0.0, le=1.0, default=1.0)
    low_db: float = Field(ge=-12, le=12, default=0.0)
    low_hz: float = Field(ge=60, le=1000, default=200.0)
    high_db: float = Field(ge=-12, le=12, default=0.0)
    high_hz: float = Field(ge=1000, le=12000, default=4000.0)
    gain_db: float = Field(ge=-6, le=12, default=0.0)
    echo_ms: int = Field(ge=0, le=600, default=0)


@app.get("/api/health")
def health():
    return {"status": "ok", "app": "voice-filter", "version": "2.0", "time": int(time.time())}


@app.get("/api/version")
def version():
    return {"app": "Voice Filter", "version": "2.0", "auth_required": bool(API_TOKEN)}


@app.get("/api/presets")
def get_presets():
    return load_presets()


@app.post("/api/presets")
def post_presets(p: PresetIn, request: Request):
    token = request.headers.get("x-voicefilter-token", "")
    authed = bool(API_TOKEN) and token == API_TOKEN
    if API_TOKEN and not authed:
        raise HTTPException(401, "invalid or missing X-VoiceFilter-Token")
    ip = _client_ip(request)
    limit = RATE_WRITE_AUTHED if authed else RATE_WRITE_OPEN
    if not _allowed(ip, "write", limit):
        raise HTTPException(429, "rate limited")
    items = load_presets()
    entry = p.model_dump()
    entry["name"] = clean_text(entry["name"], 40) or "untitled"
    entry["author"] = clean_text(entry["author"], 40) or "anonymous"
    items = [i for i in items if i.get("name") != entry["name"]]
    items.append(entry)
    save_presets(items[-MAX_PRESETS:])
    return {"saved": entry}


@app.get("/download")
@app.get("/download/VoiceFilter.apk")
def download():
    candidates = sorted(
        (f for f in os.listdir(APK_DIR) if f.endswith(".apk")),
        key=lambda n: os.path.getmtime(os.path.join(APK_DIR, n)),
        reverse=True,
    ) if os.path.isdir(APK_DIR) else []
    if not candidates:
        raise HTTPException(404, "apk not built yet")
    path = os.path.join(APK_DIR, candidates[0])
    return FileResponse(path, filename=candidates[0],
                          media_type="application/vnd.android.package-archive")


@app.get("/", response_class=HTMLResponse)
def index():
    with open(os.path.join(STATIC, "index.html")) as f:
        return f.read()


@app.get("/hero.png")
def hero():
    for p in (os.path.join(STATIC, "hero.png"), os.path.join(ROOT, "assets_src", "hero_1200.png")):
        if os.path.exists(p):
            return FileResponse(p, media_type="image/png")
    raise HTTPException(404, "hero image missing")
