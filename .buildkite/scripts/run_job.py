#!/usr/bin/env python3
# -*- encoding: utf-8

import argparse
import os
import subprocess
import sys

from git_utils import get_changed_paths, git, remote_default_head, remote_default_branch, local_current_head, get_sha1_for_tag
from sbt_dependency_tree import Repository


def check_call(cmd):
    """
    A wrapped version of subprocess.check_call() that doesn't print a
    traceback if the command errors.
    """
    print("*** Running %r" % " ".join(cmd))
    try:
        return subprocess.check_call(cmd)
    except subprocess.CalledProcessError as err:
        print(err)
        sys.exit(err.returncode)


def make(*args):
    """Run a Make command, and check it completes successfully."""
    check_call(["make"] + list(args))


def should_run_sbt_project(repo, project_name, changed_paths):
    project = repo.get_project(project_name)

    interesting_paths = [p for p in changed_paths if not p.startswith(".sbt_metadata")]

    if ".travis.yml" in interesting_paths:
        print("*** Relevant: .travis.yml")
        return True
    if "build.sbt" in interesting_paths:
        print("*** Relevant: build.sbt")
        return True
    if any(p.startswith("project/") for p in interesting_paths):
        print("*** Relevant: project/")
        return True

    for path in interesting_paths:
        if path.startswith((".travis", "docs/")):
            continue

        if path.endswith(".tf"):
            continue

        if path.endswith("Makefile"):
            if os.path.dirname(project.folder) == os.path.dirname(path):
                print("*** %s is defined by %s" % (project.name, path))
                return True
            else:
                continue

        try:
            project_for_path = repo.lookup_path(path)
        except KeyError:
            # This path isn't associated with a project!
            print("*** Unrecognised path: %s" % path)
            return True
        else:
            if project.depends_on(project_for_path):
                print("*** %s depends on %s" % (project.name, project_for_path.name))
                return True

        print("*** Not significant: %s" % path)

    return False


def current_branch():
    return os.environ["BUILDKITE_BRANCH"]


def get_commit_range():
    """Provide commit range between remote default head, and local head."""
    remote_head = remote_default_head()
    local_head = local_current_head()

    return f"{remote_head}..{local_head}"


if __name__ == "__main__":
    # Get git metadata

    current_branch_name = current_branch()
    default_branch_name = remote_default_branch()

    commit_range = None
    local_head = local_current_head()

    is_change_to_default_branch = current_branch_name == default_branch_name

    if is_change_to_default_branch:
        latest_sha = get_sha1_for_tag("latest")
        commit_range = f"{latest_sha}..{local_head}"
    else:
        remote_head = remote_default_head()
        commit_range = f"{remote_head}..{local_head}"

    print(f"Branch: {current_branch_name}")
    print(f"Commit range: {commit_range}")

    # Parse script args

    parser = argparse.ArgumentParser()
    parser.add_argument("project_name", default=os.environ.get("SBT_PROJECT"))
    parser.add_argument("--changes-in", nargs="*")
    args = parser.parse_args()

    # Get change_globs

    if args.changes_in:
        change_globs = args.changes_in + [".buildkite/pipeline.yml"]
    else:
        change_globs = None

    # Get changed_paths

    if is_change_to_default_branch:
        changed_paths = get_changed_paths("HEAD", "master", globs=change_globs)
    else:
        git("fetch", "origin")
        changed_paths = get_changed_paths(commit_range, globs=change_globs)

    # Determine whether we should build this project

    sbt_repo = Repository(".sbt_metadata")
    try:
        if not should_run_sbt_project(sbt_repo, args.project_name, changed_paths):
            print(f"Nothing in this patch affects {args.project_name}, so stopping.")
            sys.exit(0)
    except (FileNotFoundError, KeyError):
        if args.changes_in and not changed_paths:
            print(f"Nothing in this patch affects the files {args.changes_in}, so stopping.")
            sys.exit(0)

    # Perform make tasks

    make(f"{args.project_name}-test")

    if is_change_to_default_branch:
        make(f"{args.project_name}-publish")
