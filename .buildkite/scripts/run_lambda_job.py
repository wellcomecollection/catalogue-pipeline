#!/usr/bin/env python3

from commands import run_build_script, _subprocess_run
from provider import is_default_branch


if __name__ == "__main__":
    lambda_paths = [
        "calm_adapter/calm_window_generator",
        "calm_adapter/calm_deletion_check_initiator" "common/window_generator",
        "sierra_adapter/s3_demultiplexer",
        "sierra_adapter/sierra_progress_reporter",
        "sierra_adapter/update_embargoed_holdings",
        "tei_adapter/tei_updater",
    ]

    for path in lambda_paths:
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
