import os
from itertools import product
from typing import Literal

from test_mocks import MockSmartOpen


def load_fixture(file_name: str) -> bytes:
    with open(f"{os.path.dirname(__file__)}/fixtures/{file_name}", "rb") as f:
        return f.read()


def add_mock_transformer_outputs(sources: list[Literal["loc", "mesh"]], node_types: list[Literal["concepts", "locations", "names"]]) -> None:
    """
    Add mock transformer output files to S3 so that the IdLabelChecker class can extract ids and labels from them.
    """
    for source, node_type in product(sources, node_types):
        MockSmartOpen.mock_s3_file(
            f"s3://bulk_load_test_bucket/{source}_{node_type}__nodes.csv",
            load_fixture(f"{source}/transformer_output_{node_type}_nodes.csv").decode(),
        )
