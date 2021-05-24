#!/usr/bin/env python3
# -*- encoding: utf-8

import argparse
import os
import sys

from commands import make
from git_utils import (
    local_current_head,
    get_sha1_for_tag,
    remote_default_head,
    get_changed_paths,
)
from provider import current_branch, is_default_branch

def should_run_lambda_tests(changed_paths):
    relevant_file_types = [
        '.py',
        '.ini',
        '.txt'
    ]
    for path in changed_paths:
        if any(path.endswith(file_type) for file_type in relevant_file_types):
            return True
    return False


if __name__ == "__main__":
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

    # Parse script args

    parser = argparse.ArgumentParser()
    parser.add_argument("project_name", default=os.environ.get("SBT_PROJECT"))
    parser.add_argument("--changes-in", nargs="*")
    args = parser.parse_args()

    # Get changed_paths

    if args.changes_in:
        change_globs = args.changes_in + [".buildkite/pipeline.yml"]
    else:
        change_globs = None

    changed_paths = get_changed_paths(commit_range, globs=change_globs)

    # Determine whether we should build this project

    if not should_run_lambda_tests(changed_paths):
        print(f"Nothing in this patch affects {args.project_name}, so stopping.")
        sys.exit(0)

    make(f"{args.project_name}-test")

    if is_default_branch():
        make(f"{args.project_name}-publish")
