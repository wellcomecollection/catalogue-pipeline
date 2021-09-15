#!/usr/bin/env python3

import os
import sys

from commands import run_build_script
from run_job import should_run_sbt_project
from git_utils import (
    local_current_head,
    get_sha1_for_tag,
    remote_default_head,
    get_changed_paths,
)
from provider import current_branch, is_default_branch
from sbt_dependency_tree import Repository


# This script takes environment variables as the "command" step
# when used with the buildkite docker plugin incorrectly parses
# spaces as newlines preventing passing args to this script!
if __name__ == "__main__":
    projects = os.environ.get("PROJECTS").split()
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

    # Determine whether we should build these projects

    sbt_repo = Repository(".sbt_metadata")
    if not any(should_run_sbt_project(sbt_repo, p, changed_paths) for p in projects):
        print(f"Nothing in this patch affects {projects}, so stopping.")
        sys.exit(0)

    for project_name in projects:
        run_build_script(
            "run_sbt_task_in_docker.sh", f"project {project_name}", "publish"
        )
