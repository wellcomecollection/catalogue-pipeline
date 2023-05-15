import contextlib
import os
import subprocess
import tempfile


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
        subprocess.check_call(["git", "clone", git_url, repo_dir])

        with working_directory(repo_dir):
            yield


def get_github_api_key(sess):
    secrets_client = sess.client("secretsmanager")

    secret_value = secrets_client.get_secret_value(
        SecretId="builds/github_wecobot/scala_libs_pr_bumps"
    )

    return secret_value["SecretString"]
