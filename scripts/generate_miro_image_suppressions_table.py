#!/usr/bin/env python3

import contextlib
import os
import shutil
import subprocess
import sys
import tempfile


def git(*args):
    """Run a Git command and return its output."""
    subprocess.check_call(["git"] + list(args))


@contextlib.contextmanager
def working_directory(path):
    """
    Changes the working directory to the given path, then returns to the
    original directory when done.
    """
    prev_cwd = os.getcwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(prev_cwd)


@contextlib.contextmanager
def cloned_repo(git_url):
    """
    Clones the repository and changes the working directory to the cloned
    repo.  Cleans up the clone when it's done.
    """
    repo_dir = tempfile.mkdtemp()

    git("clone", git_url, repo_dir)

    try:
        with working_directory(repo_dir):
            yield
    finally:
        shutil.rmtree(repo_dir)


def get_github_api_key(sess):
    secrets_client = sess.client("secretsmanager")

    secret_value = secrets_client.get_secret_value(SecretId="builds/github_wecobot/scala_libs_pr_bumps")

    return secret_value["SecretString"]
