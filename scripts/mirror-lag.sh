#!/usr/bin/env bash
# mirror-lag: compare the GitHub mirror (remote `github`) against the
# Forgejo primary (remote `origin`). The Forgejo->GitHub push mirror
# should keep them identical; divergence means the mirror stalled
# (fleet precedent 2026-10-17: domains sat 61 commits behind for ~36 h,
# caught only by a manual cross-check; 2026-10-25: webphone-android's
# workflow-scope PAT gap freezes every commit touching
# .github/workflows/).
#
# Exit codes: 0 = in sync, 1 = lag detected, 2 = environment error.
set -euo pipefail

branch="${1:-main}"

root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
	echo "mirror-lag: not inside a git work tree" >&2
	exit 2
}
cd "$root"

for remote in origin github; do
	git remote get-url "$remote" >/dev/null 2>&1 || {
		echo "mirror-lag: remote '$remote' not configured (run from the webphone-android repo)" >&2
		exit 2
	}
done

fetch() {
	git fetch --quiet "$1" "+refs/heads/${branch}:refs/remotes/$1/${branch}" || {
		echo "mirror-lag: cannot fetch remote '$1' (network / SSH / credentials?)" >&2
		exit 2
	}
}
fetch origin
fetch github

primary="$(git rev-parse "refs/remotes/origin/${branch}")"
mirror="$(git rev-parse "refs/remotes/github/${branch}")"

if [ "$primary" = "$mirror" ]; then
	echo "mirror OK: github/${branch} == origin/${branch} ($(git rev-parse --short "$primary"))"
	exit 0
fi

behind="$(git rev-list --count "refs/remotes/github/${branch}..refs/remotes/origin/${branch}")"
ahead="$(git rev-list --count "refs/remotes/origin/${branch}..refs/remotes/github/${branch}")"
echo "MIRROR LAG: github/${branch} is ${behind} commit(s) behind origin/${branch}" >&2
if [ "$ahead" -gt 0 ]; then
	echo "  ... and ${ahead} ahead (divergence, not pure lag)" >&2
fi
echo "  mirror head : $(git log -1 --format='%h %ci' "refs/remotes/github/${branch}")" >&2
echo "  primary head: $(git log -1 --format='%h %ci' "refs/remotes/origin/${branch}")" >&2
echo "  fix: deploy SystemNix (nix run .#deploy — phase 5 attaches the push" >&2
echo "      mirror for forgejo.canonicalRepos), then check the sync PAT scope:" >&2
echo "      commits touching .github/workflows/ need repo+workflow and are" >&2
echo "      rejected without it (the sops github_token gap). Re-run to confirm." >&2
exit 1
