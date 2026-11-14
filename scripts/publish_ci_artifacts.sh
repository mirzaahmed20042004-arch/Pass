#!/usr/bin/env bash
# Uploads emulator screenshots + crash log to the repo through the GitHub
# Contents API. Large payloads go via --input file to dodge ARG_MAX limits.
set +e

repo="$GITHUB_REPOSITORY"
branch="$GITHUB_REF_NAME"

push_file() {
  path="$1"; file="$2"
  if [ ! -s "$file" ]; then echo "SKIP $path (no $file)"; return 0; fi
  sha=$(gh api "repos/$repo/contents/$path?ref=$branch" --jq .sha 2>/dev/null)
  python3 scripts/make_body.py "$path" "$file" "$sha" || { echo "FAIL encode $path"; return 0; }
  if gh api -X PUT "repos/$repo/contents/$path" --input /tmp/gh_body.json > /tmp/gh_out.txt 2>&1; then
    echo "OK $path"
  else
    echo "FAIL $path"; tail -3 /tmp/gh_out.txt
  fi
}

push_file "docs/screenshots/dashboard.png" screenshot-dashboard.png
push_file "docs/screenshots/tools.png" screenshot-tools.png
push_file "docs/screenshots/radio-controls.png" screenshot-radio-controls.png
push_file "ci-crashlog.txt" ci-crashlog.txt
echo "--- done ---"
