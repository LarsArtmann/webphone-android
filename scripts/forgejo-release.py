#!/usr/bin/env python3
"""Publish a release with installable APKs to the Forgejo forge.

Obtainium (the sideload updater) reads releases from the forge's REST
API: it lists `GET /api/v1/repos/{owner}/{repo}/releases`, takes the
release's tag as the available version, and installs the `.apk` asset
attached to it. So an "update" is exactly two things on the forge:
a tag-bearing release and an APK asset on it. This script creates
both, idempotently — re-running after a partial failure reuses the
release and replaces the asset instead of erroring.

Why a script and not `tea`/`curl`: the devShell ships neither, python3
is always present (scripts/contrast-check.py already rides it), and the
fetch/upload logic is small enough that the stdlib keeps it honest.
The token is read from FORGEJO_TOKEN and never written anywhere.

Usage:
    FORGEJO_TOKEN=... python3 scripts/forgejo-release.py \
        --tag v0.2.0 --name "webphone-android 0.2.0" \
        --notes-file work/release/0.2.0/notes.md \
        --asset work/release/0.2.0/webphone-android-0.2.0.apk

    python3 scripts/forgejo-release.py --check    # token + reachability

Exit codes: 0 = published (or check OK), 1 = publish failed,
2 = environment/usage error (bad token, unreachable forge, bad args).
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

DEFAULT_BASE_URL = os.environ.get("FORGEJO_BASE_URL", "https://forgejo.home.lan")
DEFAULT_REPO = os.environ.get("FORGEJO_REPO", "lars/webphone-android")


class ForgejoError(RuntimeError):
    """An API call the user has to act on; the message says which action."""


def make_opener(token: str, insecure: bool) -> urllib.request.OpenerDirector:
    context = ssl.create_default_context()
    if insecure:
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        print(
            "warning: --insecure disables TLS verification (only sane against the "
            "internal dnsblockd CA on the LAN/VPN forge)",
            file=sys.stderr,
        )
    opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context))
    opener.addheaders = [
        ("Authorization", f"token {token}"),
        ("Accept", "application/json"),
        ("User-Agent", "webphone-android-release"),
    ]
    return opener


def call(
    opener: urllib.request.OpenerDirector,
    method: str,
    url: str,
    payload: dict | None = None,
    expect: tuple[int, ...] = (200, 201),
):
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(request, timeout=60) as response:
            body = response.read()
            if response.status not in expect:
                raise ForgejoError(
                    f"{method} {url} -> HTTP {response.status}: {body[:300]!r}"
                )
            return json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        detail = error.read()[:400].decode(errors="replace")
        raise ForgejoError(
            f"{method} {url} -> HTTP {error.code}: {detail}\n{explain(error.code)}"
        ) from None
    except urllib.error.URLError as error:
        raise ForgejoError(
            f"{method} {url} failed: {error.reason}\n"
            "Is the forge reachable (LAN/VPN) and is the base URL right? "
            "An untrusted certificate also lands here — the forge serves the internal "
            "dnsblockd CA, so pass --insecure on hosts that do not trust it."
        ) from None


def explain(status: int) -> str:
    return {
        401: "hint: the token is missing/expired — create one in Forgejo "
        "(Settings > Applications > Generate token) with `write:repository`.",
        403: "hint: the token lacks `write:repository` (release creation needs it).",
        404: "hint: repo/owner wrong, or a private repo the token cannot see.",
        409: "hint: a release or asset with that name already exists.",
        413: "hint: the asset exceeds the forge's `[attachment] MAX_SIZE` "
        "(GET /api/v1/settings/attachment shows the current limit).",
        422: "hint: the tag/name combination is invalid (tag already used by a "
        "different release?).",
    }.get(status, "")


def upload_asset(opener, url: str, path: Path) -> dict:
    """POST one multipart/form-data upload the way Gitea/Forgejo expects."""
    boundary = uuid.uuid4().hex
    content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    head = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="attachment"; filename="{path.name}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode()
    # The APK is tens of MB: stream it from disk in chunks rather than
    # building one concatenated bytes object, so the process never holds
    # two copies of the artifact.
    tail = f"\r\n--{boundary}--\r\n".encode()

    class MultipartBody:
        def __init__(self) -> None:
            self.parts = [head, path.open("rb"), tail]

        def __iter__(self):
            for part in self.parts:
                if isinstance(part, bytes):
                    yield part
                else:
                    while chunk := part.read(1 << 20):
                        yield chunk

        def close(self) -> None:
            for part in self.parts:
                if not isinstance(part, bytes):
                    part.close()

    body = MultipartBody()
    request = urllib.request.Request(
        url,
        data=body,  # type: ignore[arg-type]  # urllib accepts any iterable of bytes
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(len(head) + path.stat().st_size + len(tail)),
        },
        method="POST",
    )
    try:
        with opener.open(request, timeout=600) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        detail = error.read()[:400].decode(errors="replace")
        raise ForgejoError(
            f"uploading {path.name} -> HTTP {error.code}: {detail}\n{explain(error.code)}"
        ) from None
    except urllib.error.URLError as error:
        raise ForgejoError(f"uploading {path.name} failed: {error.reason}") from None
    finally:
        body.close()


def find_release(opener, api: str, tag: str) -> dict | None:
    quoted = urllib.parse.quote(tag, safe="")
    try:
        return call(opener, "GET", f"{api}/releases/tags/{quoted}")
    except ForgejoError as error:
        if "HTTP 404" in str(error):
            return None
        raise


def publish(args) -> int:
    token = os.environ.get("FORGEJO_TOKEN", "").strip()
    if not token:
        print(
            "FORGEJO_TOKEN is not set. Create a token in Forgejo "
            "(Settings > Applications) with `write:repository` and export it, "
            "or write it to ~/.config/webphone-android/forgejo-token "
            "(release.sh reads that file for you).",
            file=sys.stderr,
        )
        return 2

    assets = [Path(asset) for asset in args.asset]
    for asset in assets:
        if not asset.is_file():
            print(f"asset not found: {asset}", file=sys.stderr)
            return 2

    notes = Path(args.notes_file).read_text() if args.notes_file else ""
    base = args.base_url.rstrip("/")
    api = f"{base}/api/v1/repos/{args.repo}"
    opener = make_opener(token, args.insecure)

    release = find_release(opener, api, args.tag)
    if release is None:
        print(f"creating release {args.tag} on {args.repo}")
        release = call(
            opener,
            "POST",
            f"{api}/releases",
            {
                "tag_name": args.tag,
                "name": args.name or args.tag,
                "body": notes,
                "draft": False,
                "prerelease": args.prerelease,
            },
        )
    else:
        print(f"release {args.tag} already exists (id {release['id']}); reusing it")

    release_id = release["id"]
    existing = {asset["name"]: asset for asset in release.get("assets") or []}
    for asset in assets:
        stale = existing.get(asset.name)
        if stale is not None:
            print(f"replacing existing asset {asset.name}")
            call(
                opener,
                "DELETE",
                f"{api}/releases/{release_id}/assets/{stale['id']}",
                expect=(204,),
            )
        print(f"uploading {asset.name} ({asset.stat().st_size / 1_048_576:.1f} MiB)")
        uploaded = upload_asset(
            opener,
            f"{api}/releases/{release_id}/assets?name={urllib.parse.quote(asset.name)}",
            asset,
        )
        if uploaded.get("size") != asset.stat().st_size:
            raise ForgejoError(
                f"uploaded size {uploaded.get('size')} != local {asset.stat().st_size} "
                f"for {asset.name} — the artifact is corrupt on the forge"
            )

    html = release.get("html_url") or f"{base}/{args.repo}/releases/tag/{args.tag}"
    print(f"release published: {html}")
    for asset in assets:
        print(f"  asset: {asset.name}")
    return 0


def check(args) -> int:
    token = os.environ.get("FORGEJO_TOKEN", "").strip()
    base = args.base_url.rstrip("/")
    opener = make_opener(token, args.insecure)
    version = call(opener, "GET", f"{base}/api/v1/version", expect=(200,))
    print(f"forge: {base} (Forgejo {version.get('version')})")
    limits = call(opener, "GET", f"{base}/api/v1/settings/attachment", expect=(200,))
    print(
        f"attachment limit: {limits.get('max_size')} MiB, {limits.get('max_files')} files max"
    )
    if not token:
        print("token: MISSING (set FORGEJO_TOKEN)")
        return 2
    user = call(opener, "GET", f"{base}/api/v1/user", expect=(200,))
    print(f"token: valid for {user.get('login')}")
    repo = call(opener, "GET", f"{base}/api/v1/repos/{args.repo}", expect=(200,))
    print(f"repo: {repo.get('full_name')} (private={repo.get('private')})")
    releases = call(
        opener,
        "GET",
        f"{base}/api/v1/repos/{args.repo}/releases?limit=5",
        expect=(200,),
    )
    if not releases:
        print("releases: none yet")
    for release in releases:
        apks = [
            a["name"] for a in release.get("assets") or [] if a["name"].endswith(".apk")
        ]
        print(f"  {release.get('tag_name')}: {', '.join(apks) or 'no APK asset'}")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--tag")
    parser.add_argument("--name")
    parser.add_argument("--notes-file")
    parser.add_argument("--asset", action="append", default=[])
    parser.add_argument("--prerelease", action="store_true")
    parser.add_argument(
        "--insecure",
        action="store_true",
        help="skip TLS verification (the LAN/VPN forge serves the internal dnsblockd CA)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="probe forge, token and repo; publish nothing",
    )
    args = parser.parse_args(argv)

    try:
        if args.check:
            return check(args)
        if not args.tag or not args.asset:
            parser.error("--tag and at least one --asset are required to publish")
        return publish(args)
    except ForgejoError as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
