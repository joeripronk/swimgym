#!/usr/bin/env bash
# bump-and-release.sh — Bump version, create a tag, confirm, then push.
# Usage: ./tools/bump-and-release.sh [major|minor|patch]
#   Defaults to "patch" if no argument is given.

#set -euo pipefail

RELEASE_TYPE="${1:-patch}"
BUILD_FILE="app/build.gradle.kts"

# ── helpers ──────────────────────────────────────────────────────────────────

die()  { echo "error: $*" >&2; exit 1; }
info() { echo "ℹ  $*"; }
warn() { echo "⚠  $*" >&2; }

# ── pre-flight checks ────────────────────────────────────────────────────────

if ! command -v git &>/dev/null; then
  die "git is not installed"
fi

if ! git rev-parse --is-inside-work-tree &>/dev/null; then
  die "not inside a git repository"
fi

if git diff --exit-code --quiet; then
  info "Working tree is clean ✓"
else
  warn "Working tree is dirty — commit or stash changes first."
  exit 1
fi

# ── read current version ─────────────────────────────────────────────────────

if ! [[ -f "$BUILD_FILE" ]]; then
  die "$BUILD_FILE not found"
fi

version_code=$(grep -oP '(?<=versionCode = )\d+' "$BUILD_FILE") || die "Could not parse versionCode"
version_name=$(grep -oP "(?<=versionName = \")\d+\.\d+" "$BUILD_FILE") || die "Could not parse versionName"

info "Current version: $version_name (code $version_code)"

# ── bump ─────────────────────────────────────────────────────────────────────

IFS='.' read -r major minor <<< "$version_name"

case "$RELEASE_TYPE" in
  major) major=$((major + 1)); minor=0 ;;
  minor) minor=$((minor + 1)) ;;
  *) ;;  # patch: no change to major/minor
esac

new_version_name="$major.$minor"
new_version_code=$((version_code + 1))

info "New version: $new_version_name (code $new_version_code)"

# ── edit build.gradle.kts ────────────────────────────────────────────────────

sed -i "s/versionCode = $version_code/versionCode = $new_version_code/" "$BUILD_FILE"
sed -i "s/versionName = \"$version_name\"/versionName = \"$new_version_name\"/" "$BUILD_FILE"

# ── confirm commit + tag ─────────────────────────────────────────────────────

TAG="v${new_version_name}p${version_code}"

echo ""
echo "This will commit the version bump and create tag $TAG."
read -rp "Continue? [y/N] " confirm

if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
  info "Aborted — changes not committed."
  exit 0
fi

git add "$BUILD_FILE"
git commit -m "bump: ${version_name} → ${new_version_name}"
git tag -a "$TAG" -m "Release $new_version_name"

info "Tag created: $TAG"

# ── confirm push ─────────────────────────────────────────────────────────────

echo ""
echo "This will push the commit and tag to the remote."
read -rp "Push? [y/N] " confirm

if [[ "$confirm" =~ ^[Yy]$ ]]; then
  info "Pushing commit and tag …"
  git push
  git push origin "$TAG"
  info "Done ✓"
else
  info "Aborted — nothing was pushed."
  echo "You can still push manually with:"
  echo "  git push && git push origin $TAG"
fi
