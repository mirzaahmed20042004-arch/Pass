#!/usr/bin/env python3
"""Optional local-first Ollama gateway for Ultron Ai; Python stdlib only.

The Android app can talk directly to Ollama. This gateway adds a simple bearer-token
boundary for a trusted LAN, but plain HTTP is NOT encrypted; use HTTPS/secure tunneling
for untrusted networks. No prompts, tokens, or model replies are logged or stored.
"""
import argparse
import hmac
import ipaddress
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 64 * 1024
MAX_RESPONSE = 1024 * 1024
MODEL_PATTERN = re.compile(r"^[A-Za-z0-9_.:/-]{1,120}$")


def validate_upstream(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Ollama URL must be HTTP(S) without credentials, query, or fragment")
    return url.rstrip("/")


def validate_chat(obj):
    if not isinstance(obj, dict) or not isinstance(obj.get("model"), str) or not MODEL_PATTERN.fullmatch(obj["model"]):
        raise ValueError("A valid model is required")
    messages = obj.get("messages")
    if not isinstance(messages, list) or not 1 <= len(messages) <= 10:
        raise ValueError("Provide between 1 and 10 messages")
    clean = []
    total = 0
    for msg in messages:
        if not isinstance(msg, dict) or msg.get("role") not in ("system", "user", "assistant"):
            raise ValueError("Invalid message role")
        content = msg.get("content")
        if not isinstance(content, str) or not content or len(content) > 12000:
            raise ValueError("Invalid message content")
        total += len(content)
        if total > 48000:
            raise ValueError("Conversation is too long")
        clean.append({"role": msg["role"], "content": content})
    # Streaming and arbitrary tools/options are deliberately not forwarded.
    return {"model": obj["model"], "messages": clean, "stream": False}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class GatewayServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, server_address, upstream, token=""):
        self.upstream = validate_upstream(upstream)
        self.token = token
        super().__init__(server_address, GatewayHandler)


class GatewayHandler(BaseHTTPRequestHandler):
    server_version = "UltronGateway/0.1"
    sys_version = ""

    def log_message(self, fmt, *args):
        # Request URLs and bodies can contain sensitive data. Deliberately quiet.
        return

    def respond(self, status, body):
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def authorized(self):
        expected = self.server.token
        if not expected:
            return True
        supplied = self.headers.get("Authorization", "")
        return hmac.compare_digest(supplied, "Bearer " + expected)

    def do_GET(self):
        if self.path == "/health":
            self.respond(200, {"ok": True, "service": "ultron-gateway", "version": "0.1.0"})
            return
        if not self.authorized():
            self.respond(401, {"error": "Unauthorized"})
            return
        if self.path != "/api/tags":
            self.respond(404, {"error": "Not found"})
            return
        self.proxy("/api/tags")

    def do_POST(self):
        if not self.authorized():
            self.respond(401, {"error": "Unauthorized"})
            return
        if self.path != "/api/chat":
            self.respond(404, {"error": "Not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.respond(400, {"error": "Invalid content length"})
            return
        if length <= 0 or length > MAX_BODY:
            self.respond(413, {"error": "Request size is invalid"})
            return
        if self.headers.get_content_type() != "application/json":
            self.respond(415, {"error": "Content-Type must be application/json"})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            clean = validate_chat(payload)
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
            self.respond(400, {"error": str(exc)[:200]})
            return
        self.proxy("/api/chat", clean)

    def proxy(self, path, payload=None):
        body = json.dumps(payload).encode("utf-8") if payload is not None else None
        req = urllib.request.Request(self.server.upstream + path, data=body, headers={"Accept": "application/json", "Content-Type": "application/json"}, method="POST" if body else "GET")
        opener = urllib.request.build_opener(NoRedirect)
        try:
            with opener.open(req, timeout=120) as response:
                raw = response.read(MAX_RESPONSE + 1)
                if len(raw) > MAX_RESPONSE:
                    self.respond(502, {"error": "Upstream response too large"})
                    return
                result = json.loads(raw.decode("utf-8"))
                if not isinstance(result, dict):
                    raise ValueError("Invalid response")
                self.respond(200, result)
        except urllib.error.HTTPError as exc:
            # Do not pass through arbitrary upstream content.
            self.respond(502, {"error": "Ollama returned HTTP " + str(exc.code)})
        except (OSError, ValueError, UnicodeDecodeError, json.JSONDecodeError):
            self.respond(502, {"error": "Could not reach or understand Ollama"})


def is_loopback(host):
    if host.lower() == "localhost":
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


def main():
    parser = argparse.ArgumentParser(description="Optional Ultron Ai gateway for a local Ollama server")
    parser.add_argument("--host", default="127.0.0.1", help="Listen address (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--ollama", default=os.environ.get("OLLAMA_URL", "http://127.0.0.1:11434"))
    args = parser.parse_args()
    token = os.environ.get("ULTRON_GATEWAY_TOKEN", "")
    if not is_loopback(args.host) and not token:
        parser.error("Set ULTRON_GATEWAY_TOKEN before listening on a non-loopback interface")
    if token and (len(token) < 16 or len(token) > 256 or "\n" in token or "\r" in token):
        parser.error("ULTRON_GATEWAY_TOKEN must be 16–256 characters without line breaks")
    try:
        server = GatewayServer((args.host, args.port), args.ollama, token)
    except (ValueError, OSError) as exc:
        parser.error(str(exc))
    print("Ultron gateway listening on %s:%s (HTTP; use a trusted network or HTTPS proxy)" % server.server_address, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping.", file=sys.stderr)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
