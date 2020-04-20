# -*- encoding: utf-8

import subprocess
import sys


def git(*args):
    """Run a Git command and return its output."""
    cmd = ["git"] + list(args)
    try:
        return subprocess.check_output(cmd).decode("utf8").strip()
    except subprocess.CalledProcessError as err:
        print(err)
        sys.exit(err.returncode)


def get_changed_paths(*args, globs=[]):
    """
    Returns a set of changed paths in a given commit range.

    :param args: Arguments to pass to ``git diff``.
    :param globs: List of file globs to include in changed paths.
    """
    if globs:
        args = list(args) + ["--", *globs]
    diff_output = git("diff", "--name-only", *args)

    return set([line.strip() for line in diff_output.splitlines()])
