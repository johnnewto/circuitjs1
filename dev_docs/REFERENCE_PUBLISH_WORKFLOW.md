# Reference Publish Workflow

Use this workflow to publish a curated subset of `dev_docs/` into:

- `src/com/lushprojects/circuitjs1/public/docs/reference/`

This keeps internal docs private by default while exposing only approved references in-app.

## Files

- Whitelist: `dev_docs/reference_publish_whitelist.txt`
- Sync script: `dev_docs/sync_reference_docs.sh`

## Whitelist format

Each non-comment line is:

`source_md|public_target_md|display_title`

Example:

`EQUATION_TABLE_REFERENCE.md|EquationTableReference.md|EquationTable Reference`

## Run

Dry run:

`bash dev_docs/sync_reference_docs.sh --dry-run`

Publish:

`bash dev_docs/sync_reference_docs.sh`

## What it does

1. Validates every whitelisted source file exists in `dev_docs/`.
2. Copies each source markdown file to `public/docs/reference/` using the target filename.
3. Regenerates `ReferenceIndex.md` from whitelist titles.

## Notes

- Keep user-facing docs in whitelist; leave internal notes out.
- If you need a custom whitelist path:

`bash dev_docs/sync_reference_docs.sh --whitelist path/to/whitelist.txt`
