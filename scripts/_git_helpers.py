import functools
import os
import subprocess


@functools.lru_cache()
def repo_root():
    return (
        subprocess.check_output(["git", "rev-parse", "--show-toplevel"])
        .strip()
        .decode("utf8")
    )


def ignore_directory(dirname):
    """
    Tell Git to ignore a directory.
    """
    full_path = os.path.abspath(dirname)

    # See https://alexwlchan.net/2015/06/git-info-exclude/
    exclude_path = os.path.join(repo_root(), ".git", "info", "exclude")

    with open(exclude_path, "a") as outfile:
        outfile.write(os.path.relpath(full_path, repo_root()) + "\n")
