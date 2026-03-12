#!/usr/bin/env python3
import re
import sys
from pathlib import Path


START_RE = re.compile(r"^\s*([A-Za-z_]\w*)\s*<-\s*sfcr_set\(\s*$")


def block_tag(name: str) -> str:
    if name.endswith("_eqs"):
        return "@equations"
    if name.endswith("_ext"):
        return "@parameters"
    if name.endswith("_init"):
        return "@initial"
    raise ValueError(
        f"Unknown block name '{name}'. Expected suffix _eqs, _ext, or _init."
    )


def convert(input_path: Path, output_path: Path) -> None:
    lines = input_path.read_text(encoding="utf-8").splitlines()
    output = []

    inside = False
    current_block = None

    def next_significant_line(start_index: int):
        for j in range(start_index, len(lines)):
            s = lines[j].strip()
            if s == "" or s.startswith("#"):
                continue
            return s
        return None

    for i, raw in enumerate(lines, start=1):
        start_match = START_RE.match(raw)
        if start_match:
            if inside:
                raise ValueError(f"Line {i}: Nested sfcr_set block is not allowed.")
            current_block = start_match.group(1)
            output.append(f"{block_tag(current_block)} {current_block}")
            inside = True
            continue

        if inside and raw.strip() == ")":
            inside = False
            current_block = None
            output.append("")
            continue

        if not inside:
            if raw.strip() == "":
                output.append("")
                continue
            if raw.lstrip().startswith("#"):
                output.append(raw)
                continue
            raise ValueError(
                f"Line {i}: Content outside sfcr_set block is not allowed: {raw.strip()}"
            )

        stripped = raw.strip()
        if stripped == "" or stripped.startswith("#"):
            output.append(raw)
            continue

        # Split inline comments while preserving hint text exactly.
        code, sep, comment = raw.partition("#")
        code_r = code.rstrip()

        has_trailing_comma = code_r.endswith(",")
        if has_trailing_comma:
            code_r = code_r[:-1].rstrip()
        else:
            next_sig = next_significant_line(i)
            if next_sig != ")":
                raise ValueError(
                    f"Line {i}: Expected trailing comma in sfcr_set assignment: {raw.strip()}"
                )

        if "~" not in code_r:
            raise ValueError(
                f"Line {i}: Expected '~' assignment operator in: {raw.strip()}"
            )

        left, right = code_r.split("~", 1)
        left = left.rstrip()
        right = right.lstrip()

        if left.strip() == "" or right.strip() == "":
            raise ValueError(f"Line {i}: Invalid assignment: {raw.strip()}")

        converted = f"{left} = {right}"
        if sep:
            converted = f"{converted}  #{comment}"
        output.append(converted)

    if inside:
        raise ValueError("Unclosed sfcr_set block at end of file.")

    output_path.write_text("\n".join(output) + "\n", encoding="utf-8")


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: convert_sfcr_to_blocks_strict.py <input-file> <output-file>",
            file=sys.stderr,
        )
        return 2

    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])

    try:
        convert(input_path, output_path)
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    print(f"Wrote {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
