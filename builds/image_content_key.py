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
import subprocess
import sys
import zipfile


def normalise(data, build_root):
    return data.replace(build_root, b"BUILD_ROOT")


def hash_file(path, build_root):
    with open(path, "rb") as f:
        data = f.read()
    if not path.endswith(".jar"):
        return hashlib.sha256(normalise(data, build_root)).hexdigest()
    # Hash entry names and contents, not the zip bytes, so the result does not depend on how the jar was written
    h = hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in sorted(z.namelist()):
            h.update(name.encode() + b"\0")
            h.update(hashlib.sha256(normalise(z.read(name), build_root)).digest())
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
