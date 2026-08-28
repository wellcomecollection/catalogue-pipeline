#!/usr/bin/env python3
"""
Build a deployment ZIP for a Lambda, and upload it to Amazon S3.

Usage:
  publish_lambda_zip.py <LAMBDA_SOURCE_DIR>

"""


import os
import shutil
import subprocess
import sys
import tempfile
import zipfile


BUCKET = "wellcomecollection-platform-infra"
KEY_PREFIX = "lambdas/"


def cmd(*args):
    return subprocess.check_output(list(args)).decode("utf8").strip()


def git(*args):
    return cmd("git", *args)


ROOT = git("rev-parse", "--show-toplevel")

ZIP_DIR = os.path.join(ROOT, ".lambda_zips")


def create_zip(src, dst):
    """
    Zip a source directory into a target directory.

    Based on https://stackoverflow.com/a/14569017/1558022
    """
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zf:
        abs_src = os.path.abspath(src)
        for dirname, subdirs, files in os.walk(src):
            for filename in files:
                if filename.startswith("."):
                    continue
                absname = os.path.abspath(os.path.join(dirname, filename))
                arcname = absname[len(abs_src) + 1 :]
                zf.write(absname, arcname)


# Matches the Lambda runtimes and builds/test.python.Dockerfile; the build
# agent's own Python is older than some of our pinned dependencies allow.
PYTHON_IMAGE = "python:3.10"


def install_requirements(reqs_file, target):
    """
    Install pinned dependencies into the target directory using the Lambda
    runtime's Python (and x86_64 Linux wheels) rather than the build agent's.
    """
    subprocess.check_call(
        [
            "docker",
            "run",
            "--rm",
            "--platform",
            "linux/amd64",
            "--user",
            f"{os.getuid()}:{os.getgid()}",
            "--volume",
            f"{os.path.abspath(reqs_file)}:/requirements.txt:ro",
            "--volume",
            f"{target}:/target",
            PYTHON_IMAGE,
            "pip",
            "install",
            "--no-cache-dir",
            "--requirement",
            "/requirements.txt",
            "--target",
            "/target",
        ]
    )


def build_lambda_local(path, name):
    """
    Construct a Lambda ZIP bundle on the local disk.  Returns the path to
    the constructed ZIP bundle.

    :param path: Path to the Lambda source code.
    """
    print(f"*** Building Lambda ZIP for {name}")
    target = tempfile.mkdtemp()

    # Copy all the associated source files to the Lambda directory.
    src = os.path.join(path, "src")
    for f in os.listdir(src):
        if f.startswith(
            (
                # Required for tests, but unneeded in our prod images
                "test_",
                "__pycache__",
                "docker-compose.yml",
                # Hidden files
                ".",
                # Virtualenv
                "venv",
                # Required for installation, not for our prod Lambdas
                "requirements.in",
                "requirements.txt",
            )
        ):
            continue

        try:
            shutil.copy(
                src=os.path.join(src, f), dst=os.path.join(target, os.path.basename(f))
            )
        except IsADirectoryError:
            shutil.copytree(
                src=os.path.join(src, f), dst=os.path.join(target, os.path.basename(f))
            )

    # Now install any additional pip dependencies.
    for reqs_file in [
        os.path.join(path, "requirements.txt"),
        os.path.join(path, "src", "requirements.txt"),
    ]:
        if os.path.exists(reqs_file):
            print(f"*** Installing dependencies from {reqs_file}")
            install_requirements(reqs_file=reqs_file, target=target)
        else:
            print(f"*** No requirements.txt found at {reqs_file}")

    print(f"*** Creating zip bundle for {name}")
    os.makedirs(ZIP_DIR, exist_ok=True)
    src = target
    dst = os.path.join(ZIP_DIR, name)
    create_zip(src=src, dst=dst)
    return dst


if __name__ == "__main__":
    try:
        lambda_dir = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <LAMBDA_DIR>")

    bucket = BUCKET
    key = f"{KEY_PREFIX}{lambda_dir}"

    name = os.path.basename(key)
    filename = build_lambda_local(path=lambda_dir, name=name)

    subprocess.check_call(["aws", "s3", "cp", filename, f"s3://{bucket}/{key}.zip"])
