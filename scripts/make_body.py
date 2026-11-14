#!/usr/bin/env python3
"""Builds the JSON body for a GitHub contents PUT (base64 payload on disk)."""
import base64, json, os, sys

path, file_path = sys.argv[1], sys.argv[2]
sha = sys.argv[3] if len(sys.argv) > 3 else ""
with open(file_path, "rb") as fh:
    content = base64.b64encode(fh.read()).decode()
body = {
    "message": ("ci: update " if sha else "ci: add ") + path + " [skip ci]",
    "content": content,
    "branch": os.environ["GITHUB_REF_NAME"],
}
if sha:
    body["sha"] = sha
with open("/tmp/gh_body.json", "w") as out:
    json.dump(body, out)
print("encoded", path, len(content), "b64 chars")
