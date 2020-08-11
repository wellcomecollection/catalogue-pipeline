#!/usr/bin/env python3
# -*- encoding: utf-8

import git_utils as git


if __name__ == "__main__":
    # Get git metadata
    branch_name = git.current_branch()

    if git.is_default_branch():
        print(f"Successful completion on {branch_name}, tagging latest.")
        git.git("tag", "latest", "--force")
        git.git("push", "origin", "latest", "--force")
        print("Done.")
    else:
        print(f"Successful completion on {branch_name}, nothing to do.")
