#!/usr/bin/env python
# Shim — real code is in graph/steps/extractor.py
from graph.steps.extractor import *  # noqa: F401, F403

if __name__ == "__main__":
    from argparse import ArgumentParser

    parser = ArgumentParser(description="")
    parser.add_argument(
        "--use-cli",
        action="store_true",
        help="Whether to invoke the local CLI handler instead of the ECS handler.",
    )
    args, _ = parser.parse_known_args()

    if args.use_cli:
        local_handler(parser)  # noqa: F405
    else:
        ecs_handler(parser)  # noqa: F405
