"""VoiceMorph backend: landing page, APK download and the community preset API
that the Android app talks to (GET/POST /api/presets)."""
import json
import os
import time

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATIC = os.path.join(ROOT, "backend", "static")
PRESETS_FILE = os.path.join(ROOT, "backend", "presets.json")
APK_DIR = os.path.join(ROOT, "apk")

SEED = [
    {"name": "Deep Commander", "author": "voicemorph", "pitch": 0.72, "strength": 1.0,
     "low_db": 6.0, "low_hz": 160, "high_db": -5.0, "high_hz": 3200},
    {"name": "Radio Demon", "author": "voicemorph", "pitch": 0.88, "strength": 0.9,
     "low_db": 2.0, "low_hz": 240, "high_db": 3.0, "high_hz": 5000},
    {"name": "Bubblegum Scout", "author": "voicemorph", "pitch": 1.42, "strength": 1.0,
     "low_db": -4.0, "low_hz": 250, "high_db": 5.0, "high_hz": 5200},
    {"name": "Female Lead", "author": "voicemorph", "pitch": 1.30, "strength": 1.0,
     "low_db": -3.5, "low_hz": 220, "high_db": 4.5, "high_hz": 4500},
    {"name": "Male Lead", "author": "voicemorph", "pitch": 0.78, "strength": 1.0,
     "low_db": 4.5, "low_hz": 180, "high_db": -4.0, "high_hz": 3500},
    {"name": "Ghost Whisper", "author": "voicemorph", "pitch": 1.12, "strength": 0.75,
     "low_db": -6.0, "low_hz": 300, "high_db": 6.0, "high_hz": 6000},
]

app = FastAPI(title="VoiceMorph backend", version="1.0")
app.mount("/static", StaticFiles(directory=STATIC), name="static")


def load_presets():
    if not os.path.exists(PRESETS_FILE):
        save_presets(SEED)
        return list(SEED)
    with open(PRESETS_FILE) as f:
        return json.load(f)


def save_presets(items):
    with open(PRESETS_FILE, "w") as f:
        json.dump(items, f, indent=2)


class PresetIn(BaseModel):
    name: str = Field(min_length=1, max_length=40)
    author: str = Field(default="anonymous", max_length=40)
    pitch: float = Field(ge=0.4, le=2.5)
    strength: float = Field(ge=0.0, le=1.0, default=1.0)
    low_db: float = Field(ge=-12, le=12, default=0.0)
    low_hz: float = Field(ge=60, le=1000, default=200.0)
    high_db: float = Field(ge=-12, le=12, default=0.0)
    high_hz: float = Field(ge=1000, le=12000, default=4000.0)


@app.get("/api/health")
def health():
    return {"status": "ok", "time": int(time.time())}


@app.get("/api/presets")
def get_presets():
    return load_presets()


@app.post("/api/presets")
def post_presets(p: PresetIn):
    items = load_presets()
    entry = p.model_dump()
    items = [i for i in items if i.get("name") != entry["name"]]
    items.append(entry)
    save_presets(items[-60:])
    return {"saved": entry}


@app.get("/download")
@app.get("/download/VoiceMorph.apk")
def download():
    candidates = sorted(
        (f for f in os.listdir(APK_DIR) if f.endswith(".apk")),
        key=lambda n: os.path.getmtime(os.path.join(APK_DIR, n)),
        reverse=True,
    ) if os.path.isdir(APK_DIR) else []
    if not candidates:
        raise HTTPException(404, "apk not built yet")
    path = os.path.join(APK_DIR, candidates[0])
    return FileResponse(path, filename=candidates[0], media_type="application/vnd.android.package-archive")


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
