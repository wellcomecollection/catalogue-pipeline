#!/usr/bin/env python3
"""
Print a key for the content of an sbt app's Docker image, so CI can tell when an
image would be the same as one already published (wellcomecollection/catalogue-pipeline#3858).

The key covers:
  - the staged app (target/universal/stage), with the build directory replaced
    by a placeholder: -Xcheckinit compiles absolute source paths into classes,
    and those change with the CI agent's checkout path;
  - the Dockerfile;
  - the digest of the base image the build used;
  - the ISO week, so every app is rebuilt at least weekly and picks up the
    `apk upgrade` in its Dockerfile.

Usage: image_content_key.py PROJECT_DIRECTORY BUILD_ROOT [BASE_IMAGE_DIGEST]
If BASE_IMAGE_DIGEST is omitted it is read from the local Docker daemon.
"""

import datetime
import hashlib
import io
import os
import re
import struct
import subprocess
import sys
import zipfile


def normalise(data, build_root):
    return data.replace(build_root, b"BUILD_ROOT")


# Size of each constant pool entry after its tag byte, by tag; Utf8 (1) is length-prefixed
CONSTANT_SIZES = {
    3: 4,
    4: 4,
    5: 8,
    6: 8,
    7: 2,
    8: 2,
    9: 4,
    10: 4,
    11: 4,
    12: 4,
    15: 3,
    16: 2,
    17: 4,
    18: 4,
    19: 2,
    20: 2,
}


def normalise_class(data, build_root):
    """
    Rewrite a class file's UTF-8 constants with the build directory replaced, length prefixes included,
    so classes built under checkout paths of different lengths compare equal.
    """
    if data[:4] != b"\xca\xfe\xba\xbe":
        return normalise(data, build_root)
    out = bytearray(data[:10])
    count = struct.unpack(">H", data[8:10])[0]
    i, pos = 1, 10
    while i < count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack(">H", data[pos + 1 : pos + 3])[0]
            value = normalise(data[pos + 3 : pos + 3 + length], build_root)
            out += bytes([1]) + struct.pack(">H", len(value)) + value
            pos += 3 + length
        elif tag in CONSTANT_SIZES:
            out += data[pos : pos + 1 + CONSTANT_SIZES[tag]]
            pos += 1 + CONSTANT_SIZES[tag]
        else:
            # An unknown tag means a format this parser doesn't know, so fall back to replacing raw bytes
            return normalise(data, build_root)
        # Long and Double constants take two slots
        i += 2 if tag in (5, 6) else 1
    return bytes(out) + normalise(data[pos:], build_root)


def hash_entry(name, data, build_root):
    if name.endswith(".class"):
        return hashlib.sha256(normalise_class(data, build_root)).digest()
    return hashlib.sha256(normalise(data, build_root)).digest()


def hash_file(path, build_root):
    with open(path, "rb") as f:
        data = f.read()
    if not path.endswith(".jar"):
        return hash_entry(path, data, build_root).hex()
    # Hash entry names and contents, not the zip bytes, so the result does not depend on how the jar was written
    h = hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in sorted(z.namelist()):
            h.update(name.encode() + b"\0")
            h.update(hash_entry(name, z.read(name), build_root))
    return h.hexdigest()


def base_image_digest(dockerfile):
    images, stages = [], set()
    with open(dockerfile) as f:
        for line in f:
            if not re.match(r"^\s*FROM\s", line, re.IGNORECASE):
                continue
            words = [w for w in line.split()[1:] if not w.startswith("--")]
            # A FROM naming an earlier stage is not a base image
            if words[0] not in stages:
                images.append(words[0])
            if len(words) >= 3 and words[1].lower() == "as":
                stages.add(words[2])
    digests = []
    for image in images:
        if "@sha256:" in image:
            digests.append(image)
            continue
        # BuildKit may not keep the base image in the local store, so fall back to asking the registry
        local = subprocess.run(
            ["docker", "image", "inspect", "--format", "{{json .RepoDigests}}", image],
            check=False,
            capture_output=True,
            text=True,
        )
        found = (
            re.findall(r"@(sha256:[0-9a-f]{64})", local.stdout)
            if local.returncode == 0
            else []
        )
        if found:
            digests.append(f"{image}={found[0]}")
            continue
        raw = subprocess.run(
            ["docker", "buildx", "imagetools", "inspect", "--raw", image],
            check=True,
            capture_output=True,
        ).stdout
        digests.append(f"{image}=sha256:{hashlib.sha256(raw).hexdigest()}")
    return ";".join(digests)


def content_key(project_directory, build_root, base_digest):
    stage = os.path.join(project_directory, "target", "universal", "stage")
    if not os.path.isdir(stage):
        sys.exit(f"No staged app at {stage}; run sbt stage first")
    root = build_root.rstrip("/").encode()

    h = hashlib.sha256()
    for dirpath, dirnames, filenames in os.walk(stage):
        dirnames.sort()
        for name in sorted(filenames):
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, stage)
            executable = os.access(path, os.X_OK)
            h.update(f"{rel}\0{int(executable)}\0{hash_file(path, root)}\n".encode())

    dockerfile = os.path.join(project_directory, "Dockerfile")
    with open(dockerfile, "rb") as f:
        h.update(b"Dockerfile\0" + hashlib.sha256(f.read()).digest())
    h.update(f"base\0{base_digest}\n".encode())

    year, week, _ = datetime.datetime.now(datetime.timezone.utc).isocalendar()
    h.update(f"week\0{year}-W{week:02d}\n".encode())
    return h.hexdigest()


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    project_directory, build_root = sys.argv[1], sys.argv[2]
    base = (
        sys.argv[3]
        if len(sys.argv) == 4
        else base_image_digest(os.path.join(project_directory, "Dockerfile"))
    )
    print(content_key(project_directory, build_root, base))
