#!/usr/bin/env python3
"""
Verify EVS diag raw frames:
1) compute SHA-256
2) compute non-zero ratio
3) optionally convert UYVY raw to PNG (requires Pillow)
"""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
from typing import Iterable


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def non_zero_ratio(data: bytes) -> float:
    if not data:
        return 0.0
    non_zero = sum(1 for b in data if b != 0)
    return non_zero / float(len(data))


def yuv_to_rgb(y: int, u: int, v: int) -> tuple[int, int, int]:
    c = y - 16
    d = u - 128
    e = v - 128
    r = (298 * c + 409 * e + 128) >> 8
    g = (298 * c - 100 * d - 208 * e + 128) >> 8
    b = (298 * c + 516 * d + 128) >> 8
    return max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b))


def uyvy_to_rgb_image(raw: bytes, width: int, height: int) -> bytes:
    expected = width * height * 2
    if len(raw) < expected:
        raise ValueError(f"raw too small: {len(raw)} < {expected}")
    rgb = bytearray(width * height * 3)
    si = 0
    di = 0
    while si + 3 < expected and di + 5 < len(rgb):
        u = raw[si]
        y0 = raw[si + 1]
        v = raw[si + 2]
        y1 = raw[si + 3]
        r0, g0, b0 = yuv_to_rgb(y0, u, v)
        r1, g1, b1 = yuv_to_rgb(y1, u, v)
        rgb[di : di + 3] = bytes((r0, g0, b0))
        rgb[di + 3 : di + 6] = bytes((r1, g1, b1))
        si += 4
        di += 6
    return bytes(rgb)


def maybe_convert_png(raw: bytes, width: int, height: int, out_png: Path) -> bool:
    try:
        from PIL import Image  # type: ignore
    except Exception:
        return False

    rgb = uyvy_to_rgb_image(raw, width, height)
    image = Image.frombytes("RGB", (width, height), rgb)
    image.save(out_png)
    return True


def iter_raw_files(raw_dir: Path) -> Iterable[Path]:
    for p in sorted(raw_dir.glob("*.raw")):
        if p.is_file():
            yield p


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("session_dir", help="Session directory containing raw/")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=896)
    parser.add_argument("--png", action="store_true", help="Also render PNG files")
    args = parser.parse_args()

    session = Path(args.session_dir).resolve()
    raw_dir = session / "raw"
    out_dir = session / "png_from_python"
    out_dir.mkdir(parents=True, exist_ok=True)

    if not raw_dir.exists():
        print(f"[ERR] raw dir not found: {raw_dir}")
        return 2

    expected = args.width * args.height * 2
    hashes = []
    print(f"[INFO] session={session}")
    print(f"[INFO] expected_bytes={expected}")

    for raw_file in iter_raw_files(raw_dir):
        data = raw_file.read_bytes()
        sha = sha256_bytes(data)
        nz = non_zero_ratio(data)
        ok_size = len(data) >= expected
        hashes.append(sha)
        line = f"{raw_file.name}: size={len(data)} ok_size={ok_size} sha256={sha} non_zero_ratio={nz:.4f}"
        if args.png and ok_size:
            out_png = out_dir / (raw_file.stem + ".png")
            png_ok = maybe_convert_png(data[:expected], args.width, args.height, out_png)
            line += f" png={out_png.name if png_ok else 'SKIP(Pillow missing)'}"
        print(line)

    uniq = len(set(hashes))
    total = len(hashes)
    print(f"[SUMMARY] unique_hash={uniq}/{total}")
    if total == 0:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
