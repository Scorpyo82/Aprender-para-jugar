#!/usr/bin/env python3
"""Report exact JVM Code attribute sizes for AprenderApp and largest methods."""
from pathlib import Path
import struct
import sys

ROOT = Path("app/build")
TARGET_CLASS = "MainActivityKt.class"
TARGET_METHOD = "AprenderApp"
LIMIT = 65535


def read_u1(data, pos):
    return data[pos], pos + 1


def read_u2(data, pos):
    return struct.unpack_from(">H", data, pos)[0], pos + 2


def read_u4(data, pos):
    return struct.unpack_from(">I", data, pos)[0], pos + 4


def parse_class(path):
    data = path.read_bytes()
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError(f"Not a JVM class file: {path}")
    pos = 8
    cp_count, pos = read_u2(data, pos)
    cp = [None] * cp_count
    i = 1
    while i < cp_count:
        tag, pos = read_u1(data, pos)
        if tag == 1:
            size, pos = read_u2(data, pos)
            cp[i] = data[pos:pos + size].decode("utf-8", errors="replace")
            pos += size
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f"Unknown constant-pool tag {tag} in {path}")
        i += 1

    _, pos = read_u2(data, pos)  # access flags
    _, pos = read_u2(data, pos)  # this class
    _, pos = read_u2(data, pos)  # super class
    interfaces, pos = read_u2(data, pos)
    pos += interfaces * 2

    fields, pos = read_u2(data, pos)
    for _ in range(fields):
        pos += 6
        count, pos = read_u2(data, pos)
        for _ in range(count):
            _, pos = read_u2(data, pos)
            size, pos = read_u4(data, pos)
            pos += size

    methods, pos = read_u2(data, pos)
    found = []
    for _ in range(methods):
        _, pos = read_u2(data, pos)
        name_index, pos = read_u2(data, pos)
        descriptor_index, pos = read_u2(data, pos)
        attr_count, pos = read_u2(data, pos)
        name = cp[name_index]
        descriptor = cp[descriptor_index]
        code_length = None
        for _ in range(attr_count):
            attr_name_index, pos = read_u2(data, pos)
            attr_size, pos = read_u4(data, pos)
            attr_name = cp[attr_name_index]
            if attr_name == "Code":
                _, code_pos = read_u2(data, pos)  # max_stack
                _, code_pos = read_u2(data, code_pos)  # max_locals
                code_length, _ = read_u4(data, code_pos)
            pos += attr_size
        if code_length is not None:
            found.append((name, descriptor, code_length))
    return found


def main():
    classes = list(ROOT.rglob(TARGET_CLASS))
    if not classes:
        print(f"ERROR: {TARGET_CLASS} not found under {ROOT}")
        return 2

    methods = []
    for class_file in classes:
        methods.extend((class_file, *entry) for entry in parse_class(class_file))

    target = [entry for entry in methods if entry[1] == TARGET_METHOD]
    if not target:
        print(f"ERROR: method {TARGET_METHOD} not found in {TARGET_CLASS}")
        for class_file in classes:
            print(f"  inspected: {class_file}")
        return 2

    print("JVM BYTECODE REPORT (Code attribute code_length; exact bytes)")
    for class_file, name, descriptor, size in target:
        remaining = LIMIT - size
        percent = size * 100 / LIMIT
        print(f"{name}{descriptor}: {size:,} / {LIMIT:,} bytes ({percent:.2f}%), remaining {remaining:,}")
        print(f"  class: {class_file}")
        if size >= LIMIT:
            print("  ERROR: method has reached/exceeded the JVM method code limit.")
            return 1

    print("\nLargest methods in MainActivityKt.class:")
    for class_file, name, descriptor, size in sorted(methods, key=lambda item: item[3], reverse=True)[:10]:
        print(f"  {size:>6,} bytes  {name}{descriptor}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
