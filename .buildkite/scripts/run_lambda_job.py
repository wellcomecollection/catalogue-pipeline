#!/usr/bin/env python3
# -*- encoding: utf-8

import argparse
import os
import sys

from commands import run_build_script
from git_utils import (
    local_current_head,
    get_sha1_for_tag,
    remote_default_head,
    get_changed_paths,
)
from provider import current_branch, is_default_branch


def should_run_lambda_tests(changed_paths):
    relevant_file_types = [".py", ".ini", ".txt"]
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

    lambda_paths = [
        "calm_adapter/calm_window_generator",
        "calm_adapter/calm_deletion_check_initiator"
        "common/window_generator",
        "sierra_adapter/s3_demultiplexer",
        "sierra_adapter/sierra_progress_reporter",
        "sierra_adapter/update_embargoed_holdings",
        "tei_adapter/tei_updater",
    ]

    changed_paths = get_changed_paths(commit_range)

    for path in lambda_paths:
        if not any(
            p.endswith((".py", ".ini", ".txt")) and p.startswith(path)
            for p in changed_paths
        ):
            print(f"*** Nothing in this patch affects {path}, skipping")
            continue

        run_build_script("run_lambda_tests.sh", path)

        if is_default_branch():
            run_lambda_tests("publish_lambda_zip.py", path, "--bucket", "wellcomecollection-platform-infra", "--key", f"lambdas/{path}.zip")
