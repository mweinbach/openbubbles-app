#!/usr/bin/env python3
"""Execute exported OpenAbsinthe proof p-code against a captured oracle tuple.

This is a recovery-time validation tool, not production code. It turns the
Ghidra export into an architecture-neutral behavioral oracle so the recovered
implementation can be checked without loading or executing the Android ELF.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path


WRAPPER = 0x1B45224
IMAGE_BIAS = 0x100000
STACK_TOP = 0x700000000000
STATE_ADDR = 0x500000000000
NONCE_ADDR = 0x500000001000
MESSAGE_ADDR = 0x500000002000
OUTPUT_ADDR = 0x500000003000
HEAP_BASE = 0x600000000000

ALLOC = 0x1824514
DEALLOC = 0x1824518
ALLOC_ZEROED = 0x1824520
ALLOC_GUARD = 0x182452C
MEMCPY = 0x22D2D10
MEMSET = 0x22D2D40
PANICS = {0x22BD8EC, 0x22CAAAC, 0x22CBA10, 0x22CBB54, 0x22CBD50}


@dataclass(frozen=True)
class Varnode:
    space: str
    offset: int
    size: int


@dataclass
class Instruction:
    address: int
    next_address: int
    assembly: str
    operations: list[tuple[str, list[Varnode | None]]]


def parse_varnode(text: str) -> Varnode | None:
    if text == "-":
        return None
    space, offset, size, *_ = text.split(":")
    return Varnode(space, int(offset, 16), int(size))


def parse_program(path: Path) -> dict[int, Instruction]:
    program: dict[int, Instruction] = {}
    current: Instruction | None = None
    for line in path.read_text().splitlines():
        fields = line.split("|")
        if fields[0] == "I":
            current = Instruction(
                int(fields[1], 16), int(fields[2], 16), fields[3], []
            )
            program[current.address] = current
        elif fields[0] == "P":
            assert current is not None
            current.operations.append(
                (fields[1], [parse_varnode(field) for field in fields[2:]])
            )
    return program


def mask(size: int) -> int:
    return (1 << (size * 8)) - 1


def signed(value: int, size: int) -> int:
    sign = 1 << (size * 8 - 1)
    value &= mask(size)
    return value - (1 << (size * 8)) if value & sign else value


class ByteSpace:
    def __init__(self) -> None:
        self.bytes: dict[int, int] = {}

    def read(self, address: int, size: int) -> int:
        return sum(self.bytes.get(address + index, 0) << (index * 8) for index in range(size))

    def write(self, address: int, size: int, value: int) -> None:
        for index in range(size):
            self.bytes[address + index] = (value >> (index * 8)) & 0xFF


class Memory(ByteSpace):
    def __init__(self, image: bytes) -> None:
        super().__init__()
        self.image = image
        self.static_pages: set[int] = set()

    def read(self, address: int, size: int) -> int:
        value = 0
        for index in range(size):
            location = address + index
            if location in self.bytes:
                byte = self.bytes[location]
            else:
                image_offset = location - IMAGE_BIAS
                if 0 <= image_offset < len(self.image):
                    byte = self.image[image_offset]
                    self.static_pages.add(location & ~0xFFF)
                else:
                    byte = 0
            value |= byte << (index * 8)
        return value

    def load(self, address: int, data: bytes) -> None:
        for index, byte in enumerate(data):
            self.bytes[address + index] = byte

    def dump(self, address: int, size: int) -> bytes:
        return bytes(self.read(address + index, 1) for index in range(size))


class Machine:
    def __init__(self, program: dict[int, Instruction], image: bytes) -> None:
        self.program = program
        self.registers = ByteSpace()
        self.unique = ByteSpace()
        self.memory = Memory(image)
        self.heap_next = HEAP_BASE
        self.steps = 0

    def read(self, node: Varnode | None) -> int:
        assert node is not None
        if node.space in {"const", "ram"}:
            return node.offset & mask(node.size)
        if node.space == "register":
            return self.registers.read(node.offset, node.size)
        if node.space == "unique":
            return self.unique.read(node.offset, node.size)
        raise RuntimeError(f"unsupported address space {node.space}")

    def write(self, node: Varnode | None, value: int) -> None:
        assert node is not None
        value &= mask(node.size)
        if node.space == "register":
            self.registers.write(node.offset, node.size, value)
        elif node.space == "unique":
            self.unique.write(node.offset, node.size, value)
        else:
            raise RuntimeError(f"cannot write {node.space}")

    def register(self, offset: int, value: int) -> None:
        self.registers.write(offset, 8, value)

    def allocate(self, size: int, alignment: int, zeroed: bool) -> int:
        alignment = max(alignment, 1)
        address = (self.heap_next + alignment - 1) & -alignment
        self.heap_next = address + max(size, 1) + 0x10
        if zeroed:
            self.memory.write(address, size, 0)
        return address

    def external_call(self, target: int) -> None:
        x0 = self.registers.read(0x4000, 8)
        x1 = self.registers.read(0x4008, 8)
        x2 = self.registers.read(0x4010, 8)
        if target == ALLOC:
            self.register(0x4000, self.allocate(x0, x1, False))
        elif target == ALLOC_ZEROED:
            self.register(0x4000, self.allocate(x0, x1, True))
        elif target in {DEALLOC, ALLOC_GUARD}:
            pass
        elif target == MEMCPY:
            data = self.memory.dump(x1, x2)
            self.memory.load(x0, data)
            self.register(0x4000, x0)
        elif target == MEMSET:
            self.memory.load(x0, bytes([x1 & 0xFF]) * x2)
            self.register(0x4000, x0)
        elif target in PANICS:
            raise RuntimeError(f"recovered circuit reached panic call {target:#x}")
        else:
            raise RuntimeError(f"unhandled external call {target:#x}")

    def multiply_long(self, output: Varnode, left: Varnode, right: Varnode, width: int) -> None:
        left_value = self.read(left)
        right_value = self.read(right)
        lanes = left.size // width
        lane_mask = mask(width)
        result = 0
        for lane in range(lanes):
            shift = lane * width * 8
            product = ((left_value >> shift) & lane_mask) * ((right_value >> shift) & lane_mask)
            result |= product << (lane * width * 16)
        self.write(output, result)

    def execute_operation(
        self, opcode: str, nodes: list[Varnode | None]
    ) -> int | None:
        output = nodes[0]
        inputs = nodes[1:]
        values = [self.read(node) for node in inputs]
        output_size = output.size if output is not None else 0

        if opcode == "COPY":
            self.write(output, values[0])
        elif opcode == "INT_ADD":
            self.write(output, values[0] + values[1])
        elif opcode == "INT_SUB":
            self.write(output, values[0] - values[1])
        elif opcode == "INT_MULT":
            self.write(output, values[0] * values[1])
        elif opcode == "INT_DIV":
            self.write(output, values[0] // values[1])
        elif opcode == "INT_AND":
            self.write(output, values[0] & values[1])
        elif opcode == "INT_OR":
            self.write(output, values[0] | values[1])
        elif opcode == "INT_XOR":
            self.write(output, values[0] ^ values[1])
        elif opcode == "INT_NEGATE":
            self.write(output, ~values[0])
        elif opcode == "INT_2COMP":
            self.write(output, -values[0])
        elif opcode == "INT_LEFT":
            self.write(output, values[0] << values[1])
        elif opcode == "INT_RIGHT":
            self.write(output, values[0] >> values[1])
        elif opcode == "INT_ZEXT":
            self.write(output, values[0])
        elif opcode == "SUBPIECE":
            self.write(output, values[0] >> (values[1] * 8))
        elif opcode == "INT_EQUAL":
            self.write(output, int(values[0] == values[1]))
        elif opcode == "INT_NOTEQUAL":
            self.write(output, int(values[0] != values[1]))
        elif opcode == "INT_SLESS":
            assert inputs[0] is not None and inputs[1] is not None
            self.write(output, int(signed(values[0], inputs[0].size) < signed(values[1], inputs[1].size)))
        elif opcode == "INT_LESSEQUAL":
            self.write(output, int(values[0] <= values[1]))
        elif opcode == "INT_CARRY":
            assert inputs[0] is not None
            self.write(output, int(values[0] + values[1] > mask(inputs[0].size)))
        elif opcode == "INT_SCARRY":
            assert inputs[0] is not None
            bits = inputs[0].size * 8
            total = signed(values[0], inputs[0].size) + signed(values[1], inputs[0].size)
            self.write(output, int(total < -(1 << (bits - 1)) or total > (1 << (bits - 1)) - 1))
        elif opcode == "INT_SBORROW":
            assert inputs[0] is not None
            bits = inputs[0].size * 8
            total = signed(values[0], inputs[0].size) - signed(values[1], inputs[0].size)
            self.write(output, int(total < -(1 << (bits - 1)) or total > (1 << (bits - 1)) - 1))
        elif opcode == "BOOL_NEGATE":
            self.write(output, int(not values[0]))
        elif opcode == "BOOL_AND":
            self.write(output, int(bool(values[0]) and bool(values[1])))
        elif opcode == "BOOL_OR":
            self.write(output, int(bool(values[0]) or bool(values[1])))
        elif opcode == "LOAD":
            assert output is not None
            self.write(output, self.memory.read(values[1], output.size))
        elif opcode == "STORE":
            assert inputs[2] is not None
            self.memory.write(values[1], inputs[2].size, values[2])
        elif opcode == "BRANCH":
            target = values[0]
            if target not in self.program:
                self.external_call(target)
                return self.registers.read(0x40F0, 8)
            return target
        elif opcode == "CBRANCH":
            if values[1]:
                return values[0]
        elif opcode == "CALL":
            target = values[0]
            if target in self.program:
                return target
            self.external_call(target)
        elif opcode == "RETURN":
            return values[0]
        elif opcode == "BRANCHIND":
            raise RuntimeError("recovered circuit reached indirect trap branch")
        elif opcode == "CALLOTHER":
            if values[0] == 0x115:
                assert output is not None and inputs[1] is not None and inputs[2] is not None
                self.multiply_long(output, inputs[1], inputs[2], values[3])
            else:
                raise RuntimeError(f"unhandled p-code user operation {values[0]:#x}")
        else:
            raise RuntimeError(f"unsupported p-code operation {opcode}")
        return None

    def run(self) -> None:
        pc = WRAPPER
        while pc:
            self.steps += 1
            if self.steps > 20_000_000:
                raise RuntimeError(f"instruction limit at {pc:#x}")
            try:
                instruction = self.program[pc]
            except KeyError as error:
                raise RuntimeError(f"no exported instruction at {pc:#x}") from error
            next_pc = instruction.next_address
            operation_index = 0
            while operation_index < len(instruction.operations):
                opcode, nodes = instruction.operations[operation_index]
                target = nodes[1] if len(nodes) > 1 else None
                if opcode in {"BRANCH", "CBRANCH"} and target is not None and target.space == "const":
                    taken = opcode == "BRANCH" or bool(self.read(nodes[2]))
                    operation_index += signed(target.offset, target.size) if taken else 1
                    continue
                destination = self.execute_operation(opcode, nodes)
                if destination is not None:
                    next_pc = destination
                    break
                operation_index += 1
            pc = next_pc


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pcode", type=Path, required=True)
    parser.add_argument("--elf", type=Path, required=True)
    parser.add_argument("--oracle", type=Path)
    parser.add_argument("--seed", type=lambda value: int(value, 0))
    parser.add_argument("--state")
    parser.add_argument("--nonce")
    parser.add_argument("--message")
    parser.add_argument("--expected")
    args = parser.parse_args()

    values = json.loads(args.oracle.read_text()) if args.oracle else vars(args)
    if args.seed is not None:
        state_value = args.seed & 0xFFFFFFFFFFFFFFFF

        def generated(length: int) -> str:
            nonlocal state_value
            output = bytearray()
            for _ in range(length):
                state_value = (
                    state_value * 6364136223846793005 + 1442695040888963407
                ) & 0xFFFFFFFFFFFFFFFF
                output.append(state_value >> 56)
            return output.hex()

        if args.oracle:
            values["nonce"] = generated(16)
            values.pop("expected", None)
        else:
            values = {
                "state": generated(576),
                "nonce": generated(16),
                "message": generated(480),
            }
    if not all(values.get(name) for name in ("state", "nonce", "message")):
        parser.error("provide --oracle or all of --state, --nonce, and --message")
    state = bytes.fromhex(values["state"])
    nonce = bytes.fromhex(values["nonce"])
    message = bytes.fromhex(values["message"])
    if len(state) != 576 or len(nonce) != 16 or len(message) != 480:
        parser.error("state, nonce, and message must be 576, 16, and 480 bytes")

    machine = Machine(parse_program(args.pcode), args.elf.read_bytes())
    machine.memory.load(STATE_ADDR, state)
    machine.memory.load(NONCE_ADDR, nonce)
    machine.memory.load(MESSAGE_ADDR, message)
    machine.register(0x8, STACK_TOP)
    machine.register(0x4000, STATE_ADDR)
    machine.register(0x4008, NONCE_ADDR)
    machine.register(0x4010, MESSAGE_ADDR)
    machine.register(0x4040, OUTPUT_ADDR)
    machine.register(0x40F0, 0)
    machine.run()

    proof = machine.memory.dump(OUTPUT_ADDR, 16).hex()
    print(f"proof={proof}")
    print(f"instructions={machine.steps}")
    print("static_pages=" + ",".join(f"{page:#x}" for page in sorted(machine.memory.static_pages)))
    expected = args.expected or values.get("expected")
    if expected and proof != expected.lower():
        raise SystemExit(f"proof mismatch: expected {expected.lower()}")


if __name__ == "__main__":
    main()
