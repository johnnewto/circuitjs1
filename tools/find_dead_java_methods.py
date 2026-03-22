#!/usr/bin/env python3

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import List


DECL_RE = re.compile(
    r"^\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?[\w<>,\[\]\.\s]+\s+(\w+)\s*\([^;]*\)\s*\{\s*$"
)


@dataclass
class MethodInfo:
    name: str
    decl_line: int
    end_line: int
    wrapper_like: bool


def run_git_grep(symbol: str) -> List[str]:
    cmd = ["git", "grep", "-n", "-w", symbol, "--", "*.java"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode not in (0, 1):
        raise RuntimeError(result.stderr.strip() or f"git grep failed for {symbol}")
    return [line for line in result.stdout.splitlines() if line.strip()]


def parse_ref(ref_line: str):
    file_path, line_no, _ = ref_line.split(":", 2)
    return file_path, int(line_no)


def is_wrapper_like(body_lines: List[str]) -> bool:
    filtered = []
    for line in body_lines:
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("//"):
            continue
        filtered.append(stripped)
    if len(filtered) != 2:
        return False
    if filtered[-1] != "}":
        return False
    stmt = filtered[0]
    if not stmt.endswith(";"):
        return False
    return bool(re.match(r"^(?:return\s+)?[A-Za-z_][\w\.]*\([^;]*\);$", stmt))


def parse_methods(java_file: Path) -> List[MethodInfo]:
    lines = java_file.read_text(encoding="utf-8").splitlines()
    methods: List[MethodInfo] = []

    class_depth = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        decl_match = DECL_RE.match(line)

        if decl_match and class_depth == 1:
            name = decl_match.group(1)
            if name in {"if", "for", "while", "switch", "catch", "try", "do", "synchronized"}:
                class_depth += line.count("{") - line.count("}")
                i += 1
                continue

            method_depth = 1
            j = i + 1
            body = []
            while j < len(lines) and method_depth > 0:
                body_line = lines[j]
                body.append(body_line)
                method_depth += body_line.count("{") - body_line.count("}")
                j += 1

            methods.append(
                MethodInfo(
                    name=name,
                    decl_line=i + 1,
                    end_line=j,
                    wrapper_like=is_wrapper_like(body),
                )
            )
            i = j
            continue

        class_depth += line.count("{") - line.count("}")
        i += 1

    return methods


def main() -> None:
    parser = argparse.ArgumentParser(description="Find dead and wrapper-like Java methods")
    parser.add_argument("java_file", help="Path to Java file (e.g. src/.../CirSim.java)")
    parser.add_argument("--wrappers-only", action="store_true", help="Only report wrapper-like methods")
    args = parser.parse_args()

    java_file = Path(args.java_file)
    if not java_file.exists():
        raise SystemExit(f"File not found: {java_file}")

    methods = parse_methods(java_file)
    if not methods:
        print("No top-level methods found")
        return

    print("status\tmethod\tdecl_line\tinternal_refs\texternal_refs\twrapper_like")
    for method in methods:
        refs = run_git_grep(method.name)
        internal_refs = 0
        external_refs = 0
        for ref in refs:
            ref_file, ref_line = parse_ref(ref)
            if ref_file == str(java_file):
                if method.decl_line <= ref_line <= method.end_line:
                    continue
                internal_refs += 1
            else:
                external_refs += 1

        if args.wrappers_only and not method.wrapper_like:
            continue

        status = "DEAD" if (internal_refs == 0 and external_refs == 0) else "USED"
        print(
            f"{status}\t{method.name}\t{method.decl_line}\t{internal_refs}\t{external_refs}\t{method.wrapper_like}"
        )


if __name__ == "__main__":
    main()
