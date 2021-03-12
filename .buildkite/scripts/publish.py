# -*- encoding: utf-8

import os
import sys

from commands import git, sbt
from run_job import should_run_sbt_project
from git_utils import (
    local_current_head,
    get_sha1_for_tag,
    remote_default_head,
    get_changed_paths,
)
from provider import current_branch, is_default_branch
from sbt_dependency_tree import Repository

ROOT = git("rev-parse", "--show-toplevel")

BUILD_SBT = os.path.join(ROOT, "build.sbt")


def update_build_sbt():
    new_version_string = new_version()

    print("New version: %s" % new_version_string)
    lines = list(open(BUILD_SBT))
    for idx, l in enumerate(lines):
        if l.startswith("val projectVersion = "):
            lines[idx] = 'val projectVersion = "%s"\n' % new_version_string.strip("v")
            break
        else:  # no break
            raise RuntimeError("Never updated version in build.sbt?")

    with open(BUILD_SBT, "w") as f:
        f.write("".join(lines))


def new_version():
    commit_hash = git("rev-parse", "HEAD")
    build_number = os.environ["BUILDKITE_BUILD_NUMBER"]

    new_version = [build_number, commit_hash]
    new_version = tuple(new_version)
    return "v" + ".".join(map(str, new_version))


def publish(project_name):
    sbt(f"project {project_name}", "publish")


# This script takes environment variables as the "command" step
# when used with the buildkite docker plugin incorrectly parses
# spaces as newlines preventing passing args to this script!
if __name__ == "__main__":
    project = os.environ.get("PROJECT")
    commit_range = None
    local_head = local_current_head()

    if is_default_branch():
        latest_sha = get_sha1_for_tag("latest")
        commit_range = f"{latest_sha}..{local_head}"
    else:
        remote_head = remote_default_head()
        commit_range = f"{remote_head}..{local_head}"

    print(f"Working in branch: {current_branch()}")
    print(f"On default branch: {is_default_branch()}")
    print(f"Commit range: {commit_range}")

    changed_paths = get_changed_paths(commit_range, globs=None)

    # Determine whether we should build this project

    sbt_repo = Repository(".sbt_metadata")
    if not should_run_sbt_project(sbt_repo, project, changed_paths):
        print(f"Nothing in this patch affects {project}, so stopping.")
        sys.exit(0)
    update_build_sbt()
    publish(project)
