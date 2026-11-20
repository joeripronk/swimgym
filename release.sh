#!/usr/bin/env bash
# release.sh — Sanity-check and prepare a release; bump/tag/push only with --now.
# Usage: ./release.sh [--now] [major|minor|patch]
#   Dry run by default. Pass --now to actually bump, tag and push.

set -uo pipefail

DRY_RUN=true
RELEASE_TYPE=""

for arg in "$@"; do
  case "$arg" in
    --now) DRY_RUN=false ;;
    major|minor|patch) RELEASE_TYPE="$arg" ;;
    -h|--help)
      echo "Usage: $0 [--now] [major|minor|patch]"
      echo "  (default: dry run with a patch bump)"
      exit 0
      ;;
    *) echo "error: unknown argument '$arg'" >&2; echo "Usage: $0 [--now] [major|minor|patch]" >&2; exit 1 ;;
  esac
done
RELEASE_TYPE="${RELEASE_TYPE:-patch}"

BUILD_FILE="app/build.gradle.kts"
BRANCH="main"

# ── helpers ──────────────────────────────────────────────────────────────────

die()  { echo "error: $*" >&2; exit 1; }
info() { echo "ℹ  $*"; }
warn() { echo "⚠  $*" >&2; }

# A failed sanity check is reported but non-fatal in a dry run so the plan is
# still visible; with --now it stops the release.
fail() {
  if [[ "$DRY_RUN" == true ]]; then
    warn "would abort: $*"
  else
    die "$*"
  fi
}

if [[ "$DRY_RUN" == true ]]; then
  info "DRY RUN — no files, commits, tags or pushes"
else
  info "LIVE RUN — changes will be made"
fi

# ── pre-flight checks ────────────────────────────────────────────────────────

command -v git &>/dev/null || die "git is not installed"
git rev-parse --is-inside-work-tree &>/dev/null || die "not inside a git repository"

if [[ -n "$(git status --porcelain)" ]]; then
  fail "working tree is dirty — commit or stash changes first"
  git status --short
else
  info "Working tree is clean ✓"
fi

current_branch=$(git rev-parse --abbrev-ref HEAD)
if [[ "$current_branch" != "$BRANCH" ]]; then
  fail "expected branch '$BRANCH', found '$current_branch'"
else
  info "On branch $BRANCH ✓"
fi

if git rev-parse --verify --quiet origin/"$BRANCH" &>/dev/null; then
  if [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/"$BRANCH")" ]]; then
    info "Branch is in sync with origin/$BRANCH ✓"
  else
    fail "local $BRANCH is not in sync with origin/$BRANCH — push first"
  fi
else
  warn "origin/$BRANCH not found locally — skipping remote sync check"
fi

# ── read current version ─────────────────────────────────────────────────────

[[ -f "$BUILD_FILE" ]] || die "$BUILD_FILE not found"

version_code=$(grep -oP '(?<=versionCode = )\d+' "$BUILD_FILE" | head -1)
version_prefix=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$BUILD_FILE" | head -1)
[[ -n "$version_code" && -n "$version_prefix" ]] || die "Could not parse version from $BUILD_FILE"

info "Current version: ${version_prefix}${version_code} (code $version_code)"

if [[ "$version_prefix" =~ ^([0-9]+)\.([0-9]+)(.*)$ ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  suffix="${BASH_REMATCH[3]}"
else
  major=""
  minor=""
  suffix=""
fi

case "$RELEASE_TYPE" in
  major)
    [[ -n "$major" ]] || die "cannot bump major: versionName prefix '$version_prefix' is not numeric"
    major=$((major + 1))
    minor=0
    ;;
  minor)
    [[ -n "$minor" ]] || die "cannot bump minor: versionName prefix '$version_prefix' is not numeric"
    minor=$((minor + 1))
    ;;
  patch)
    ;;
  *)
    die "usage: $0 [--now] [major|minor|patch]"
    ;;
esac

new_prefix="${major}.${minor}${suffix}"
new_version_code=$((version_code + 1))
new_version_name="${new_prefix}${new_version_code}"
TAG="v${new_version_name}"

grep -qF "versionCode = $version_code" "$BUILD_FILE" || die "versionCode line not found verbatim"
grep -qF "versionName = \"$version_prefix\"" "$BUILD_FILE" || die "versionName line not found verbatim"

if git rev-parse --verify --quiet "refs/tags/$TAG" &>/dev/null; then
  fail "tag $TAG already exists locally"
elif git ls-remote --exit-code --tags origin "refs/tags/$TAG" &>/dev/null; then
  fail "tag $TAG already exists on the remote"
else
  info "Tag $TAG is available ✓"
fi

info "New version: $new_version_name (code $new_version_code), tag $TAG"

# ── dry run: report the plan and stop ────────────────────────────────────────

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "Planned release:"
  echo "  Bump:   $BUILD_FILE — versionCode $version_code → $new_version_code, versionName \"$version_prefix\" → \"$new_prefix\""
  echo "  Commit: release swimgym v${new_version_name}"
  echo "  Tag:    $TAG (annotated — 'Release $new_version_name')"
  echo "  Push:   git push && git push origin $TAG"
  echo ""
  echo "No changes were made. To perform it, run: $0 --now $RELEASE_TYPE"
  exit 0
fi

# ── bump, commit, tag, push ──────────────────────────────────────────────────

sed -i "s/versionCode = $version_code/versionCode = $new_version_code/" "$BUILD_FILE"
sed -i "s/versionName = \"$version_prefix\"/versionName = \"$new_prefix\"/" "$BUILD_FILE"

grep -qF "versionCode = $new_version_code" "$BUILD_FILE" || die "version bump failed (versionCode)"
grep -qF "versionName = \"$new_prefix\"" "$BUILD_FILE" || die "version bump failed (versionName)"

git add "$BUILD_FILE" || die "Failed to stage $BUILD_FILE"
git commit -m "release swimgym v${new_version_name}" || die "Failed to commit version bump"
git tag -a "$TAG" -m "Release $new_version_name" || die "Failed to create tag $TAG (create it manually with: git tag -a $TAG -m 'Release $new_version_name')"

info "Tag created: $TAG"

info "Pushing commit and tag …"
git push || die "Failed to push commit"
git push origin "$TAG" || die "Failed to push tag $TAG"
info "Done ✓"
