"""Shared argparse helpers for adapter CLI entrypoints."""

from __future__ import annotations

import argparse
import typing

from adapters.extractors.oai_pmh.registry import AdapterType


def add_adapter_event_args(parser: argparse.ArgumentParser) -> None:
    """Add common adapter CLI arguments to the given parser."""
    parser.add_argument(
        "--adapter-type",
        required=True,
        choices=typing.get_args(AdapterType),
        help="Which adapter to use",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use the S3 Tables catalog instead of local storage",
    )
