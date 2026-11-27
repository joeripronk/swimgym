#!/usr/bin/env bash
# fetch_schedule.sh — Fetch Virtuagym schedule pages from a supplied curl command
# and run the app's real parser against the saved pages.
#
# Usage:
#   ./tools/fetch_schedule.sh --curl-file /path/to/curl.txt [options]
#   cat /path/to/curl.txt | ./tools/fetch_schedule.sh [options]
#
# Options:
#   --weeks N     Number of weeks to fetch (default 4, matching the app)
#   --out DIR     Directory for the downloaded HTML (default: fresh temp dir)
#   --no-test     Download only, do not run the fixture test
#
# The supplied curl command is never executed. Only its URL and headers are
# read, and cookie values are never printed or written to the repository.

set -uo pipefail

die() { echo "error: $*" >&2; exit 1; }
info() { echo "ℹ  $*"; }
warn() { echo "⚠  $*" >&2; }

REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
CURL_FILE=""
WEEKS=4
OUT=""
RUN_TEST=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --curl-file)
      [[ $# -ge 2 ]] || die "--curl-file needs a path"
      CURL_FILE="$2"; shift 2 ;;
    --weeks)
      [[ $# -ge 2 ]] || die "--weeks needs a number"
      WEEKS="$2"; shift 2 ;;
    --out)
      [[ $# -ge 2 ]] || die "--out needs a directory"
      OUT="$2"; shift 2 ;;
    --no-test) RUN_TEST=false; shift ;;
    -h|--help)
      sed -n '2,14p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$WEEKS" =~ ^[1-9][0-9]*$ ]] || die "--weeks must be a positive integer"

if [[ -n "$CURL_FILE" ]]; then
  [[ -f "$CURL_FILE" ]] || die "curl file not found: $CURL_FILE"
  CURL_CMD=$(<"$CURL_FILE")
elif [[ ! -t 0 ]]; then
  CURL_CMD=$(cat)
else
  die "provide a curl command via --curl-file or stdin"
fi
[[ -n "${CURL_CMD//[[:space:]]/}" ]] || die "empty curl command"

command -v curl >/dev/null || die "curl is not installed"
command -v python3 >/dev/null || die "python3 is not installed"

mapfile -t parsed < <(printf '%s' "$CURL_CMD" | python3 -c '
import sys, shlex, base64
tokens = shlex.split(sys.stdin.read())
url = next((t for t in tokens if t.startswith("http://") or t.startswith("https://")), "")
if not url:
    sys.exit("no URL found in curl command")
headers = {}
for i, token in enumerate(tokens[:-1]):
    if token in ("-H", "--header"):
        header = tokens[i + 1]
        if ":" in header:
            name, value = header.split(":", 1)
            headers[name.strip().lower()] = value.strip()
def b64(value):
    return base64.b64encode(value.encode()).decode()
print(b64(url))
print(b64(headers.get("cookie", "")))
print(b64(headers.get("user-agent", "")))
') || die "could not parse the curl command"

[[ ${#parsed[@]} -eq 3 ]] || die "could not parse the curl command"
url=$(printf '%s' "${parsed[0]}" | base64 -d)
cookie=$(printf '%s' "${parsed[1]}" | base64 -d)
user_agent=$(printf '%s' "${parsed[2]}" | base64 -d)
[[ -n "$cookie" ]] || die "no Cookie header found in the curl command"

base_url=${url%%\?*}
if [[ -z "$OUT" ]]; then
  OUT=$(mktemp -d "${TMPDIR:-/tmp}/swimgym-schedule.XXXXXX")
fi
mkdir -p "$OUT"
info "Saving pages to $OUT"

total=0
for (( offset = 0; offset < WEEKS; offset++ )); do
  date=$(python3 -c '
import datetime, sys
today = datetime.date.today()
monday = today - datetime.timedelta(days=today.weekday())
print((monday - datetime.timedelta(weeks=1 - int(sys.argv[1]))).isoformat())
' "$offset")

  for event_type in 8 5; do
    page_url="$base_url?event_type=$event_type"
    target="$OUT/week-$date-e$event_type.html"
    status=$(curl -sS --compressed -o "$target" -w '%{http_code}' \
      -H "Cookie: $cookie" \
      -H "User-Agent: ${user_agent:-Mozilla/5.0}" \
      -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8' \
      -H 'Accept-Language: en-US,en;q=0.9' \
      -H "Referer: $page_url" \
      "$page_url") || { warn "curl failed for $date feed $event_type"; continue; }

    if [[ "$status" != 200 ]]; then
      warn "HTTP $status for $date feed $event_type"
      continue
    fi
    if python3 -c 'import sys; raise SystemExit(0 if "btn-login" in open(sys.argv[1], errors="ignore").read() else 1)' "$target"; then
      warn "$date feed $event_type looks like a login page — cookies may be expired"
    fi
    count=$(python3 -c 'import sys; print(open(sys.argv[1], errors="ignore").read().count("class=\"class "))' "$target")
    info "$date feed $event_type: $count classes"
    total=$((total + 1))
  done
done

[[ "$total" -gt 0 ]] || die "no pages were fetched successfully"

if [[ "$RUN_TEST" == true ]]; then
  info "Running the schedule parser against the fetched pages …"
  # Force the fixture test to run again with the freshly downloaded pages.
  rm -rf "$REPO_ROOT/app/build/test-results/testDebugUnitTest"
  (cd "$REPO_ROOT" && SWIMGYM_SCHEDULE_FIXTURE_DIR="$OUT" \
    ./gradlew :app:testDebugUnitTest --tests '*ScheduleFetchFixtureTest' --no-daemon --console=plain) \
    || die "fixture test failed; pages are in $OUT"
fi

info "Done. Pages: $OUT"
