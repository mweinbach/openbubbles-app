#!/usr/bin/env python3
"""Repack an ELF64 shared object so every PT_LOAD supports 16 KiB pages.

This preserves virtual addresses and inserts file padding between load segments.
It is intended for pinned, already-linked Android libraries whose program
headers use smaller page alignment. No code, data, relocation, or symbol bytes
are rewritten.
"""

from __future__ import annotations

import argparse
import struct
from pathlib import Path


ELF_HEADER_SIZE = 64
PROGRAM_HEADER_SIZE = 56
SECTION_HEADER_SIZE = 64
PT_LOAD = 1
EM_AARCH64 = 183
PAGE_SIZE = 16 * 1024


def unpack_from(data: bytes | bytearray, offset: int, format_: str) -> tuple[int, ...]:
    return struct.unpack_from("<" + format_, data, offset)


def pack_into(data: bytearray, offset: int, format_: str, *values: int) -> None:
    struct.pack_into("<" + format_, data, offset, *values)


def parse_header(data: bytes) -> tuple[int, int, int, int, int, int]:
    if len(data) < ELF_HEADER_SIZE or data[:4] != b"\x7fELF":
        raise ValueError("input is not an ELF file")
    if data[4] != 2 or data[5] != 1:
        raise ValueError("only little-endian ELF64 inputs are supported")
    machine = unpack_from(data, 18, "H")[0]
    if machine != EM_AARCH64:
        raise ValueError(f"expected AArch64 ELF machine {EM_AARCH64}, found {machine}")

    program_offset = unpack_from(data, 32, "Q")[0]
    section_offset = unpack_from(data, 40, "Q")[0]
    program_entry_size = unpack_from(data, 54, "H")[0]
    program_count = unpack_from(data, 56, "H")[0]
    section_entry_size = unpack_from(data, 58, "H")[0]
    section_count = unpack_from(data, 60, "H")[0]
    if program_entry_size != PROGRAM_HEADER_SIZE:
        raise ValueError(f"unexpected program-header size {program_entry_size}")
    if section_entry_size != SECTION_HEADER_SIZE:
        raise ValueError(f"unexpected section-header size {section_entry_size}")
    return (
        program_offset,
        program_count,
        section_offset,
        section_count,
        program_entry_size,
        section_entry_size,
    )


def program_headers(data: bytes, offset: int, count: int) -> list[tuple[int, int, int, int]]:
    headers = []
    for index in range(count):
        start = offset + index * PROGRAM_HEADER_SIZE
        type_ = unpack_from(data, start, "I")[0]
        file_offset = unpack_from(data, start + 8, "Q")[0]
        virtual_address = unpack_from(data, start + 16, "Q")[0]
        file_size = unpack_from(data, start + 32, "Q")[0]
        headers.append((type_, file_offset, virtual_address, file_size))
    return headers


def insertion_plan(loads: list[tuple[int, int, int]]) -> list[tuple[int, int]]:
    """Return (old file offset, bytes inserted there) pairs."""
    insertions: list[tuple[int, int]] = []
    cumulative_delta = 0
    previous_end = 0
    for file_offset, virtual_address, file_size in sorted(loads):
        minimum = max(file_offset + cumulative_delta, previous_end)
        remainder = virtual_address % PAGE_SIZE
        new_offset = minimum + (remainder - minimum) % PAGE_SIZE
        new_delta = new_offset - file_offset
        if new_delta < cumulative_delta:
            raise ValueError("load segments cannot be laid out without moving bytes backward")
        if new_delta > cumulative_delta:
            insertions.append((file_offset, new_delta - cumulative_delta))
            cumulative_delta = new_delta
        previous_end = new_offset + file_size
    return insertions


def mapped_offset(old_offset: int, insertions: list[tuple[int, int]]) -> int:
    if old_offset == 0:
        return 0
    return old_offset + sum(size for threshold, size in insertions if old_offset >= threshold)


def repack(source: bytes) -> bytes:
    (
        program_offset,
        program_count,
        section_offset,
        section_count,
        _,
        _,
    ) = parse_header(source)
    headers = program_headers(source, program_offset, program_count)
    loads = [
        (file_offset, virtual_address, file_size)
        for type_, file_offset, virtual_address, file_size in headers
        if type_ == PT_LOAD
    ]
    if not loads:
        raise ValueError("ELF has no load segments")
    insertions = insertion_plan(loads)

    output = bytearray()
    cursor = 0
    for threshold, size in insertions:
        if threshold < cursor or threshold > len(source):
            raise ValueError("invalid insertion point")
        output.extend(source[cursor:threshold])
        output.extend(b"\0" * size)
        cursor = threshold
    output.extend(source[cursor:])

    new_section_offset = mapped_offset(section_offset, insertions)
    pack_into(output, 40, "Q", new_section_offset)

    for index, (type_, file_offset, _, _) in enumerate(headers):
        start = program_offset + index * PROGRAM_HEADER_SIZE
        pack_into(output, start + 8, "Q", mapped_offset(file_offset, insertions))
        if type_ == PT_LOAD:
            pack_into(output, start + 48, "Q", PAGE_SIZE)

    for index in range(section_count):
        old_start = section_offset + index * SECTION_HEADER_SIZE
        new_start = new_section_offset + index * SECTION_HEADER_SIZE
        old_file_offset = unpack_from(source, old_start + 24, "Q")[0]
        pack_into(output, new_start + 24, "Q", mapped_offset(old_file_offset, insertions))

    new_headers = program_headers(output, program_offset, program_count)
    for type_, file_offset, virtual_address, _ in new_headers:
        if type_ == PT_LOAD and file_offset % PAGE_SIZE != virtual_address % PAGE_SIZE:
            raise AssertionError("repacked load segment is not 16 KiB congruent")
    return bytes(output)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = args.input.read_bytes()
    result = repack(source)
    args.output.write_bytes(result)
    print(f"repacked {len(source)} bytes to {len(result)} bytes")


if __name__ == "__main__":
    main()
