#!/usr/bin/env python3
"""Compile exported proof p-code and its lookup tables into source data."""

from __future__ import annotations

import argparse
import base64
import hashlib
import struct
from pathlib import Path

from run_pcode import parse_program


EXPECTED_IMAGE_SHA256 = "d8e7fc7f7a56d93634b7fe827ae7f2a8be3a39dd29d4a200a81cb7d959c110da"

OPCODES = {
    name: index
    for index, name in enumerate(
        (
            "COPY",
            "INT_ADD",
            "INT_SUB",
            "INT_MULT",
            "INT_DIV",
            "INT_AND",
            "INT_OR",
            "INT_XOR",
            "INT_NEGATE",
            "INT_2COMP",
            "INT_LEFT",
            "INT_RIGHT",
            "INT_ZEXT",
            "SUBPIECE",
            "INT_EQUAL",
            "INT_NOTEQUAL",
            "INT_SLESS",
            "INT_LESSEQUAL",
            "INT_CARRY",
            "INT_SCARRY",
            "INT_SBORROW",
            "BOOL_NEGATE",
            "BOOL_AND",
            "BOOL_OR",
            "LOAD",
            "STORE",
            "BRANCH",
            "CBRANCH",
            "CALL",
            "RETURN",
            "BRANCHIND",
            "CALLOTHER",
        )
    )
}

SPACES = {"const": 0, "register": 1, "unique": 2, "ram": 3}

# Ghidra addresses include the analysis rebase. These are the only table
# ranges read by every reachable success path in the recovered circuit.
STATIC_RANGES = (
    (0x289000, 0x7000),
    (0x32C000, 0x20000),
)


def compile_program(path: Path) -> tuple[int, list[int], bytes]:
    instructions = parse_program(path)
    base = min(instructions)
    slots = (max(instructions) - base) // 4 + 1
    offsets = [0xFFFFFFFF] * slots
    encoded = bytearray()

    for address, instruction in sorted(instructions.items()):
        offsets[(address - base) // 4] = len(encoded)
        encoded.append(len(instruction.operations))
        for opcode, nodes in instruction.operations:
            encoded.extend((OPCODES[opcode], len(nodes)))
            for node in nodes:
                if node is None:
                    encoded.append(0)
                    continue
                if not 0 < node.size < 32:
                    raise ValueError(f"unsupported varnode size {node.size}")
                encoded.append((SPACES[node.space] << 5) | node.size)
                encoded.extend(struct.pack("<Q", node.offset))
    return base, offsets, bytes(encoded)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pcode", type=Path, required=True)
    parser.add_argument("--elf", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    image = args.elf.read_bytes()
    digest = hashlib.sha256(image).hexdigest()
    if digest != EXPECTED_IMAGE_SHA256:
        parser.error(f"unexpected source image SHA-256 {digest}")

    base, offsets, program = compile_program(args.pcode)
    blob = bytearray(b"OBPC1")
    blob.extend(struct.pack("<QI", base, len(offsets)))
    blob.extend(struct.pack(f"<{len(offsets)}I", *offsets))
    blob.append(len(STATIC_RANGES))
    for address, length in STATIC_RANGES:
        raw_address = address - 0x100000
        blob.extend(struct.pack("<QI", address, length))
        blob.extend(image[raw_address : raw_address + length])
    blob.extend(struct.pack("<I", len(program)))
    blob.extend(program)

    encoded = base64.b64encode(blob).decode("ascii")
    lines = [encoded[index : index + 96] for index in range(0, len(encoded), 96)]
    args.output.write_text("\n".join(lines) + "\n")
    print(
        f"wrote {args.output}: {len(blob)} decoded bytes, "
        f"{len(program)} program bytes, {len(offsets)} instruction slots"
    )


if __name__ == "__main__":
    main()
