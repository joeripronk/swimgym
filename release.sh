#!/usr/bin/env bash
# release.sh — Sanity-check, bump, tag and push a release.
# Usage: ./release.sh [--dry-run] [major|minor|patch]
#   Live by default. Pass --dry-run to preview without changing anything.

set -uo pipefail

DRY_RUN=false
RELEASE_TYPE=""

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    major|minor|patch) RELEASE_TYPE="$arg" ;;
    -h|--help)
      echo "Usage: $0 [--dry-run] [major|minor|patch]"
      echo "  (default: live run with a patch bump)"
      exit 0
      ;;
    *) echo "error: unknown argument '$arg'" >&2; echo "Usage: $0 [--dry-run] [major|minor|patch]" >&2; exit 1 ;;
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
# still visible; in a live run it stops the release.
fail() {
  if [[ "$DRY_RUN" == true ]]; then
    warn "would abort: $*"
  else
    die "$*"
  fi
}

push_failed() {
  warn "Push failed: $1"
  if [[ -n "${TAG:-}" ]]; then
    warn "The release commit and tag $TAG exist locally but were not fully pushed."
    warn "Re-run '$0' after fixing the remote, or push manually with:"
    warn "  git push origin $BRANCH && git push origin $TAG"
  else
    warn "Re-run '$0' after fixing the remote, or push manually with:"
    warn "  git push origin $BRANCH"
  fi
  die "$1"
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

local_head=$(git rev-parse HEAD)
remote_head=""

git fetch --quiet origin "$BRANCH" || fail "could not fetch origin/$BRANCH"
if git rev-parse --verify --quiet origin/"$BRANCH" &>/dev/null; then
  remote_head=$(git rev-parse origin/"$BRANCH")
else
  warn "origin/$BRANCH not found locally — skipping remote sync check"
fi

if [[ -z "$remote_head" ]]; then
  info "Remote branch unknown — skipping remote sync check"
elif [[ "$local_head" == "$remote_head" ]]; then
  info "Branch is in sync with origin/$BRANCH ✓"
elif git merge-base --is-ancestor "$remote_head" HEAD; then
  # Local is strictly ahead, so pushing is a fast-forward. Do it automatically
  # instead of aborting; only a diverged/behind branch needs manual recovery.
  pending_tags=$(git tag --no-merged "$remote_head" | tr '\n' ' ')
  pending_tags="${pending_tags% }"
  if [[ "$DRY_RUN" == true ]]; then
    info "Local $BRANCH is ahead by $(git rev-list --count "$remote_head"..HEAD) commit(s) — would push${pending_tags:+ with tag(s): $pending_tags}"
    # Treat the push as done so the remaining pre-flight checks reflect the
    # state after the real run.
    remote_head="$local_head"
  else
    info "Local $BRANCH is ahead — pushing pending commit(s) first …"
    git push origin "$BRANCH" || push_failed "Failed to push commit"
    for tag in $pending_tags; do
      git push origin "$tag" || push_failed "Failed to push tag $tag"
    done
    remote_head=$(git rev-parse origin/"$BRANCH")
    [[ "$remote_head" == "$local_head" ]] || fail "push did not bring origin/$BRANCH up to date"
    info "Branch and any pending tags are pushed ✓"
  fi
else
  fail "local $BRANCH has diverged from or is behind origin/$BRANCH — reconcile it before releasing"
fi

# ── read current version ─────────────────────────────────────────────────────

[[ -f "$BUILD_FILE" ]] || die "$BUILD_FILE not found"

version_code=$(grep -oP '(?<=versionCode = )\d+' "$BUILD_FILE" | head -1)
version_prefix=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$BUILD_FILE" | head -1)
[[ -n "$version_code" && -n "$version_prefix" ]] || die "Could not parse version from $BUILD_FILE"

current_version_name="${version_prefix}${version_code}"
current_tag="v${current_version_name}"
info "Current version: $current_version_name (code $version_code)"

if [[ "$version_prefix" =~ ^([0-9]+)\.([0-9]+)(.*)$ ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  suffix="${BASH_REMATCH[3]}"
else
  major=""
  minor=""
  suffix=""
fi
[[ -n "$major" && -n "$minor" ]] || die "unparseable versionName prefix '$version_prefix' (expected e.g. '1.0p')"

case "$RELEASE_TYPE" in
  major)
    major=$((10#$major + 1))
    minor=0
    ;;
  minor)
    minor=$((10#$minor + 1))
    ;;
  patch)
    ;;
  *)
    die "usage: $0 [--dry-run] [major|minor|patch]"
    ;;
esac

new_prefix="${major}.${minor}${suffix}"
new_version_code=$((10#$version_code + 1))
new_version_name="${new_prefix}${new_version_code}"
TAG="v${new_version_name}"

# A previous run may have committed and tagged but failed to push. Never bump
# on top of that state: it would create a second, orphaned release.
if [[ "$(git log -1 --pretty=%s)" == "release swimgym v${current_version_name}" ]]; then
  git ls-remote --exit-code --tags origin "refs/tags/$current_tag" &>/dev/null
  previous_tag_rc=$?
  if [[ "$previous_tag_rc" == 0 && -n "$remote_head" && "$local_head" == "$remote_head" ]]; then
    info "Previous release $current_version_name is fully pushed ✓"
  else
    fail "release $current_version_name is only partly pushed — push it first with 'git push origin $BRANCH && git push origin $current_tag'"
  fi
fi

grep -qF "versionCode = $version_code" "$BUILD_FILE" || die "versionCode line not found verbatim"
grep -qF "versionName = \"$version_prefix\"" "$BUILD_FILE" || die "versionName line not found verbatim"

git ls-remote --exit-code --tags origin "refs/tags/$TAG" &>/dev/null
tag_rc=$?
case "$tag_rc" in
  0) fail "tag $TAG already exists on the remote" ;;
  2) info "Tag $TAG is available ✓" ;;
  *) fail "could not check remote tags (git ls-remote exited $tag_rc)" ;;
esac
if git rev-parse --verify --quiet "refs/tags/$TAG" &>/dev/null; then
  fail "tag $TAG already exists locally"
fi

info "New version: $new_version_name (code $new_version_code), tag $TAG"

# ── dry run: report the plan and stop ────────────────────────────────────────

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "Planned release:"
  echo "  Bump:   $BUILD_FILE — versionCode $version_code → $new_version_code, versionName \"$version_prefix\" → \"$new_prefix\""
  echo "  Commit: release swimgym v${new_version_name}"
  echo "  Tag:    $TAG (annotated — 'Release $new_version_name')"
  echo "  Push:   git push origin $BRANCH && git push origin $TAG"
  echo ""
  echo "No changes were made. To perform it, run: $0 $RELEASE_TYPE"
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
git push origin "$BRANCH" || push_failed "Failed to push commit"
git push origin "$TAG" || push_failed "Failed to push tag $TAG"
info "Done ✓"
