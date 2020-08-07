#!/usr/bin/env python
# -*- encoding: utf-8

import argparse
import os
import subprocess
import sys

from git_utils import get_changed_paths, git
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


if __name__ == "__main__":
    is_ci = os.environ.get("CI") == "true" or False

    if is_ci:
        print("Detected CI environment")
    else:
        print("Detected NOT running in CI environment")

    # Ensure we are up to date
    git("remote", "update")

    default_branch = git("symbolic-ref", "refs/remotes/origin/HEAD").split("/")[-1]
    current_branch = git("rev-parse", "--abbrev-ref", "HEAD")

    ref_head_default_local = git("show-ref", f"refs/heads/{default_branch}", "-s")
    ref_head_current_local = git("show-ref", f"refs/heads/{current_branch}", "-s")

    ref_head_default_remote = git(
        "show-ref", f"refs/remotes/origin/{default_branch}", "-s"
    )
    ref_head_current_remote = git(
        "show-ref", f"refs/remotes/origin/{current_branch}", "-s"
    )

    local_default_synced = ref_head_default_local == ref_head_default_remote
    local_current_synced = ref_head_current_local == ref_head_current_remote

    if not local_default_synced:
        print(f"Local default branch ({default_branch}) is out of sync with remote")
        sys.exit(1)

    if not local_current_synced:
        print(
            f"WARNING! Local current branch ({current_branch}) is out of sync with remote"
        )
        if is_ci:
            print("Cannot continue in CI environment, out of date, cancelling build.")
            sys.exit(0)

    ref_head_default = ref_head_default_local
    ref_head_current = ref_head_current_local

    print(f"Default branch from origin: {default_branch}")
    print(f"Current branch: {current_branch}")

    is_change_request = default_branch != current_branch

    if is_change_request:
        print(f"Detected change request: {default_branch} != {current_branch}")
    else:
        print(f"Detected change in default branch: {default_branch}")

    commit_range = None
    is_change_from_default_head = ref_head_default != ref_head_current

    if is_change_from_default_head:
        commit_range = f"{ref_head_default}..{ref_head_current}"
        print(f"Detected commit range: {commit_range}")
    else:
        print(
            f"Detected no changes between default branch HEAD and current branch HEAD: {ref_head_default}"
        )

    attempt_publish = (not is_change_request) and is_change_from_default_head

    if attempt_publish:
        print("Running in default branch and changes detected, will attempt publish")

    parser = argparse.ArgumentParser()
    parser.add_argument("project_name", default=os.environ.get("SBT_PROJECT"))
    parser.add_argument("--changes-in", nargs="*")
    args = parser.parse_args()

    task = f"{args.project_name}-test"

    if args.changes_in:
        change_globs = args.changes_in + [".travis.yml"]
    else:
        change_globs = None

    if is_change_request:
        changed_paths = get_changed_paths("HEAD", "master", globs=change_globs)
    else:
        git("fetch", "origin")
        changed_paths = get_changed_paths(commit_range, globs=change_globs)

    sbt_repo = Repository(".sbt_metadata")
    try:
        if not should_run_sbt_project(sbt_repo, args.project_name, changed_paths):
            print(
                f"Nothing in this patch affects {args.project_name}, so skipping tests"
            )
            sys.exit(0)
    except (FileNotFoundError, KeyError):
        if args.changes_in and not changed_paths:
            print(
                f"Nothing in this patch affects the files {args.changes_in}, so skipping tests"
            )
            sys.exit(0)

    make(task)
    if attempt_publish:
        make(task.replace("test", "publish"))
