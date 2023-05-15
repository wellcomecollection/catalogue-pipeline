import contextlib
import os
import subprocess
import tempfile

from _common import git, get_secret_string


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
    with tempfile.TemporaryDirectory() as repo_dir:
        git(
            "clone",
            git_url,
            repo_dir,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

        with working_directory(repo_dir):
            yield


def get_github_api_key(sess):
    return get_secret_string(
        sess, secret_id="builds/github_wecobot/scala_libs_pr_bumps"
    )
