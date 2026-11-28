#!/usr/bin/env python3
"""Mock-forge publish test for scripts/forgejo-release.py.

The publish half of the release lane talks to the Forgejo v1 API. This
test stands up an in-process HTTP server that answers exactly the
endpoints the client uses, runs the REAL client against it as a
subprocess, and asserts what arrived: the release body, the tag, the
prerelease flag, the asset bytes. It never touches the real forge.

Run: python3 scripts/test-forgejo-release.py   (stdlib only, exit 1 on
any mismatch). Wired into the build gate and CI.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

CLIENT = Path(__file__).resolve().parent / "forgejo-release.py"


class MockForge:
    """The endpoints forgejo-release.py uses, with just enough state."""

    def __init__(self) -> None:
        self.releases: dict[str, dict] = {}
        self.next_id = 1
        self.uploaded: dict[str, bytes] = {}
        self.deleted_assets: list[str] = []
        self.token = "t0ken"
        self.requests: list[tuple[str, str]] = []

    def handler(self) -> type[BaseHTTPRequestHandler]:
        """A request-handler class bound to this mock's state."""

        forge = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:
                forge.handle(self)

            def do_POST(self) -> None:
                forge.handle(self)

            def do_DELETE(self) -> None:
                forge.handle(self)

            def log_message(self, format: str, *args: object) -> None:
                pass

        return Handler

    def handle(self, request: BaseHTTPRequestHandler) -> None:
        self.requests.append((request.command or "", request.path))
        path = request.path.split("?")[0]
        length = int(request.headers.get("Content-Length") or 0)
        body = request.rfile.read(length) if length else b""

        def send(status: int, payload: dict | list | None) -> None:
            data = json.dumps(payload if payload is not None else {}).encode()
            request.send_response(status)
            request.send_header("Content-Type", "application/json")
            request.send_header("Content-Length", str(len(data)))
            request.end_headers()
            request.wfile.write(data)

        if request.headers.get("Authorization") != f"token {self.token}":
            send(401, {"message": "token required"})
        elif path == "/api/v1/version":
            send(200, {"version": "15.0.10"})
        elif path == "/api/v1/settings/attachment":
            send(200, {"max_size": 2048, "max_files": 5})
        elif path == "/api/v1/user":
            send(200, {"login": "lars"})
        elif path == "/api/v1/repos/lars/webphone-android":
            send(200, {"full_name": "webphone-android", "private": False})
        elif (
            path == "/api/v1/repos/lars/webphone-android/releases"
            and request.command == "POST"
        ):
            payload = json.loads(body)
            tag = payload["tag_name"]
            if tag in self.releases:
                send(409, {"message": "release tag already exists"})
                return
            release = {**payload, "id": self.next_id, "assets": []}
            self.next_id += 1
            self.releases[tag] = release
            send(201, release)
        elif (
            path == "/api/v1/repos/lars/webphone-android/releases"
            and request.command == "GET"
        ):
            send(200, list(self.releases.values()))
        elif path.endswith("/releases/tags/v0.2.3") and request.command == "GET":
            release = self.releases.get("v0.2.3")
            send(200, release) if release else send(404, {"message": "not found"})
        elif "/assets" in path and request.command == "POST":
            match = re.search(rb'filename="([^"]+)"', body)
            name = match.group(1).decode() if match else "unknown"
            apk = body.split(b"\r\n\r\n", 1)[1].rsplit(b"\r\n--", 1)[0]
            self.uploaded[name] = apk
            release = self.releases["v0.2.3"]
            asset = {"id": len(release["assets"]) + 1, "name": name, "size": len(apk)}
            release["assets"].append(asset)
            send(201, asset)
        elif "/assets/" in path and request.command == "DELETE":
            asset_id = int(path.rsplit("/", 1)[1])
            release = self.releases["v0.2.3"]
            release["assets"] = [a for a in release["assets"] if a["id"] != asset_id]
            self.deleted_assets.append(str(asset_id))
            send(204, None)
        else:
            send(404, {"message": f"mock does not know {request.command} {path}"})


def run(
    client_args: list[str], base_url: str, token: str
) -> subprocess.CompletedProcess[str]:
    env = {**os.environ, "FORGEJO_TOKEN": token}
    return subprocess.run(
        [
            sys.executable,
            str(CLIENT),
            "--base-url",
            base_url,
            "--repo",
            "lars/webphone-android",
            *client_args,
        ],
        capture_output=True,
        text=True,
        env=env,
        check=False,
    )


def main() -> int:
    forge = MockForge()
    server = ThreadingHTTPServer(("127.0.0.1", 0), forge.handler())
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{server.server_port}"

    failures: list[str] = []

    def expect(condition: bool, what: str) -> None:
        print(("ok   " if condition else "FAIL ") + what)
        if not condition:
            failures.append(what)

    with tempfile.TemporaryDirectory() as tmp:
        apk = Path(tmp) / "webphone-android-0.2.3.apk"
        notes = Path(tmp) / "notes.md"
        payload = os.urandom(3 << 20)
        apk.write_bytes(payload)
        notes.write_text("## notes\n\nuninstall first\n")

        probe = run(["--check"], base, "wrong")
        expect(
            probe.returncode == 1 and "401" in probe.stderr,
            "a wrong token fails --check with the 401 hint",
        )

        first = run(
            [
                "--tag",
                "v0.2.3",
                "--name",
                "webphone-android 0.2.3",
                "--notes-file",
                str(notes),
                "--asset",
                str(apk),
                "--prerelease",
            ],
            base,
            forge.token,
        )
        expect(
            first.returncode == 0, f"first publish succeeds ({first.stderr.strip()})"
        )
        release = forge.releases.get("v0.2.3", {})
        expect(
            release.get("body") == "## notes\n\nuninstall first\n",
            "the release body is the notes file",
        )
        expect(
            release.get("draft") is False and release.get("prerelease") is True,
            "the release is a non-draft pre-release",
        )
        expect(
            forge.uploaded.get(apk.name) == payload,
            "the asset bytes on the forge equal the local file",
        )

        second = run(
            [
                "--tag",
                "v0.2.3",
                "--name",
                "webphone-android 0.2.3",
                "--notes-file",
                str(notes),
                "--asset",
                str(apk),
            ],
            base,
            forge.token,
        )
        expect(second.returncode == 0, f"re-publish succeeds ({second.stderr.strip()})")
        expect(
            "reusing it" in second.stdout,
            "a re-publish reuses the existing release instead of 409-ing",
        )
        expect(len(forge.deleted_assets) == 1, "a re-publish replaces the stale asset")
        expect(
            len(release["assets"]) == 1, "exactly one asset remains after the replace"
        )

        missing = run(
            ["--tag", "v0.2.3", "--asset", str(Path(tmp) / "nope.apk")],
            base,
            forge.token,
        )
        expect(missing.returncode == 2, "a missing asset stops before any network call")

        no_token = run(["--tag", "v0.2.3", "--asset", str(apk)], base, "")
        expect(
            no_token.returncode == 2 and "FORGEJO_TOKEN" in no_token.stderr,
            "no token stops with the token instruction",
        )

    server.shutdown()
    if failures:
        print(f"test-forgejo-release: {len(failures)} failure(s)", file=sys.stderr)
        return 1
    print(
        f"test-forgejo-release: all green against the mock forge ({len(forge.requests)} API calls)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
