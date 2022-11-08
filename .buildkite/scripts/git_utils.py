from commands import git


def remote_default_branch():
    """Inspect refs to discover default branch @ remote origin."""
    return git("symbolic-ref", "refs/remotes/origin/HEAD").split("/")[-1]
