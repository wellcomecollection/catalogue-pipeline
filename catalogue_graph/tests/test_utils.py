import json
import os
from itertools import product
from typing import Any, Literal

from test_mocks import MockSmartOpen

from utils.aws import VALID_SOURCE_FILES


def load_fixture(file_name: str) -> bytes:
    with open(f"{os.path.dirname(__file__)}/fixtures/{file_name}", "rb") as f:
        return f.read()


def load_json_fixture(file_name: str) -> Any:
    with open(f"{os.path.dirname(__file__)}/fixtures/{file_name}", "rb") as f:
        fixture = json.loads(f.read().decode())
        return fixture


def add_mock_transformer_outputs(
    sources: list[
        Literal["loc", "mesh", "wikidata_linked_loc", "wikidata_linked_mesh"]
    ],
    node_types: list[Literal["concepts", "locations", "names"]],
) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """
    for source, node_type in product(sources, node_types):
        if (node_type, source) in VALID_SOURCE_FILES:
            MockSmartOpen.mock_s3_file(
                f"s3://wellcomecollection-neptune-graph-loader/{source}_{node_type}__nodes.csv",
                load_fixture(
                    f"{source}/transformer_output_{node_type}_nodes.csv"
                ).decode(),
            )
