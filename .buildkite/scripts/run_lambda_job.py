#!/usr/bin/env python3

from commands import run_build_script, _subprocess_run
from git_utils import (
    local_current_head,
    get_sha1_for_tag,
    remote_default_head,
    get_changed_paths,
)
from provider import current_branch, is_default_branch


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
        "calm_adapter/calm_deletion_check_initiator" "common/window_generator",
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

        run_build_script("run_python_tests.sh", path)

        if is_default_branch():
            _subprocess_run(["pip3", "install", "--user", "boto3"])
            _subprocess_run(["pip3", "install", "--user", "docopt"])
            run_build_script(
                "publish_lambda_zip.py",
                path,
                "--bucket",
                "wellcomecollection-platform-infra",
                "--key",
                f"lambdas/{path}.zip",
            )
