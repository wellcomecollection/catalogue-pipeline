#!/usr/bin/env python3
# -*- encoding: utf-8

import os

from git_utils import get_changed_paths, git, remote_default_head, remote_default_branch, local_current_head, get_sha1_for_tag


def current_branch():
    return os.environ["BUILDKITE_BRANCH"]


if __name__ == "__main__":
    # Get git metadata
    current_branch_name = current_branch()
    default_branch_name = remote_default_branch()

    local_head = local_current_head()

    is_change_to_default_branch = current_branch_name == default_branch_name

    if is_change_to_default_branch:
        print(f"Successful completion on {current_branch_name}, tagging latest.")
        git('tag', 'latest', '--force')
        git('push', 'origin', 'latest', '--force')
        print("Done.")
    else:
        print(f"Successful completion on {current_branch_name}, nothing to do.")
