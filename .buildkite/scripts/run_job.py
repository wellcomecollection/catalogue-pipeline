#!/usr/bin/env python3

import argparse
import os

from commands import run_build_script
from provider import is_default_branch


if __name__ == "__main__":
    # Parse script args

    parser = argparse.ArgumentParser()
    parser.add_argument("project_name", default=os.environ.get("SBT_PROJECT"))
    args = parser.parse_args()

    run_build_script("run_tests.sh", args.project_name)

    if is_default_branch():
        run_build_script("publish_app.sh", args.project_name)
