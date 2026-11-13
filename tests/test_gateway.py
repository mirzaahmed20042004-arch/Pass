import importlib.util
import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

spec = importlib.util.spec_from_file_location("gateway", Path(__file__).parents[1] / "backend" / "server.py")
gateway = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gateway)


class FakeOllama(BaseHTTPRequestHandler):
    last_payload = None
    def log_message(self, *args): pass
    def do_GET(self):
        self.reply({"models": [{"name": "qwen2.5:3b"}]})
    def do_POST(self):
        FakeOllama.last_payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        self.reply({"message": {"content": "Hello from a test model"}})
    def reply(self, payload):
        body = json.dumps(payload).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)


class GatewayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.upstream = ThreadingHTTPServer(("127.0.0.1", 0), FakeOllama)
        cls.proxy = gateway.GatewayServer(("127.0.0.1", 0), "http://127.0.0.1:%d" % cls.upstream.server_port, "example-secret-token")
        cls.threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in (cls.upstream, cls.proxy)]
        for thread in cls.threads: thread.start()
        cls.url = "http://127.0.0.1:%d" % cls.proxy.server_port

    @classmethod
    def tearDownClass(cls):
        for server in (cls.proxy, cls.upstream): server.shutdown(); server.server_close()

    def request(self, path, payload=None, token="example-secret-token"):
        headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request(self.url + path, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req) as response: return response.status, json.load(response)
        except urllib.error.HTTPError as exc: return exc.code, json.load(exc)

    def test_health_and_auth(self):
        self.assertEqual(self.request("/health", token="wrong")[0], 200)
        self.assertEqual(self.request("/api/tags", token="wrong")[0], 401)
        status, data = self.request("/api/tags")
        self.assertEqual(status, 200); self.assertEqual(data["models"][0]["name"], "qwen2.5:3b")

    def test_chat_is_forwarded_but_tools_and_streaming_are_not(self):
        payload = {"model": "qwen2.5:3b", "messages": [{"role": "user", "content": "Hello"}], "stream": True, "tools": [{"danger": "ignored"}]}
        status, data = self.request("/api/chat", payload)
        self.assertEqual(status, 200); self.assertEqual(data["message"]["content"], "Hello from a test model")
        self.assertFalse(FakeOllama.last_payload["stream"]); self.assertNotIn("tools", FakeOllama.last_payload)

    def test_bad_payload_is_rejected(self):
        self.assertEqual(self.request("/api/chat", {"model": "bad name", "messages": []})[0], 400)
        self.assertEqual(self.request("/api/chat", {"model": "ok", "messages": [{"role": "tool", "content": "x"}]})[0], 400)
        self.assertEqual(self.request("/api/chat", {"model": "ok", "messages": [{"role": "user", "content": "x" * 70000}]})[0], 413)
        self.assertEqual(self.request("/elsewhere")[0], 404)

    def test_validation_and_loopback(self):
        with self.assertRaises(ValueError): gateway.validate_upstream("http://user:pass@example.com")
        self.assertTrue(gateway.is_loopback("127.0.0.1")); self.assertFalse(gateway.is_loopback("0.0.0.0"))

if __name__ == "__main__": unittest.main()
