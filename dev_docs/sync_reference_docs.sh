#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

WHITELIST_FILE="${WHITELIST_FILE:-$SCRIPT_DIR/reference_publish_whitelist.txt}"
SOURCE_DIR="$REPO_ROOT/dev_docs"
TARGET_DIR="$REPO_ROOT/src/com/lushprojects/circuitjs1/public/docs/reference"
INDEX_FILE="$TARGET_DIR/ReferenceIndex.md"
DRY_RUN=0

usage() {
  cat <<'USAGE'
Sync whitelisted docs from dev_docs/ to public/docs/reference/.

Usage:
  bash dev_docs/sync_reference_docs.sh [--dry-run] [--whitelist <path>]

Options:
  --dry-run             Print planned copy actions without writing files.
  --whitelist <path>    Use a custom whitelist file.
  -h, --help            Show this help.

Whitelist format (pipe-separated):
  source_md|public_target_md|display_title
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --whitelist)
      [[ $# -ge 2 ]] || { echo "Missing value for --whitelist" >&2; exit 1; }
      WHITELIST_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ! -f "$WHITELIST_FILE" ]]; then
  echo "Whitelist not found: $WHITELIST_FILE" >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"

index_lines=()
copy_count=0

while IFS='|' read -r source_rel target_rel title || [[ -n "${source_rel:-}" ]]; do
  line="${source_rel:-}"
  [[ -z "$line" ]] && continue
  [[ "$line" =~ ^[[:space:]]*# ]] && continue

  source_rel="$(echo "$source_rel" | xargs)"
  target_rel="$(echo "${target_rel:-}" | xargs)"
  title="$(echo "${title:-}" | xargs)"

  if [[ -z "$source_rel" || -z "$target_rel" || -z "$title" ]]; then
    echo "Invalid whitelist entry (need source|target|title): $line" >&2
    exit 1
  fi

  source_path="$SOURCE_DIR/$source_rel"
  target_path="$TARGET_DIR/$target_rel"

  if [[ ! -f "$source_path" ]]; then
    echo "Missing source file: $source_path" >&2
    exit 1
  fi

  if [[ $DRY_RUN -eq 1 ]]; then
    echo "[dry-run] copy $source_rel -> $target_rel"
  else
    cp "$source_path" "$target_path"
    echo "Copied $source_rel -> $target_rel"
  fi

  index_lines+=("- [$title]($target_rel)")
  copy_count=$((copy_count + 1))
done < "$WHITELIST_FILE"

if [[ $copy_count -eq 0 ]]; then
  echo "No docs found in whitelist: $WHITELIST_FILE" >&2
  exit 1
fi

if [[ $DRY_RUN -eq 1 ]]; then
  echo "[dry-run] would write ReferenceIndex.md with $copy_count entries"
  exit 0
fi

{
  echo "# CircuitJS Reference Index"
  echo
  echo 'Generated from `dev_docs/reference_publish_whitelist.txt` by `dev_docs/sync_reference_docs.sh`.'
  echo
  for line in "${index_lines[@]}"; do
    echo "$line"
  done
  echo
} > "$INDEX_FILE"

echo "Wrote $INDEX_FILE"
echo "Sync complete ($copy_count docs)."
