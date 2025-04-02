import argparse
import typing
from datetime import datetime

import polars as pl
from config import INGESTOR_S3_BUCKET, S3_BULK_LOAD_BUCKET_NAME
from transformers.create_transformer import TransformerType
from utils.aws import (
    df_from_s3_parquet,
    df_to_s3_parquet,
    get_csv_from_s3,
    get_neptune_client,
)

DELETED_IDS_LOG_SCHEMA = {
    "timestamp": pl.Date(),
    "id": pl.Utf8
}


def get_previous_node_ids(transformer_type: TransformerType) -> set[str]:
    s3_file_uri = f"s3://{INGESTOR_S3_BUCKET}/node_remover/previous_node_ids/{transformer_type}.parquet"
    df = df_from_s3_parquet(s3_file_uri)

    ids = pl.Series(df.select(pl.first())).to_list()
    print(f"Retrieved {len(ids)} previous node ids of type {transformer_type}.")
    return set(ids)


def get_current_node_ids(transformer_type: TransformerType) -> set[str]:
    s3_file_uri = f"s3://{S3_BULK_LOAD_BUCKET_NAME}/{transformer_type}__nodes.csv"
    
    ids = set(row[":ID"] for row in get_csv_from_s3(s3_file_uri))
    print(f"Retrieved {len(ids)} current node ids of type {transformer_type}.")
    return ids


def update_node_ids(transformer_type: TransformerType, ids: set[str]):
    s3_file_uri = f"s3://{INGESTOR_S3_BUCKET}/node_remover/previous_node_ids/{transformer_type}.parquet"
    df = pl.DataFrame(list(ids))
    df_to_s3_parquet(df, s3_file_uri)


def log_deleted_ids(transformer_type: TransformerType, ids: set[str]):
    s3_file_uri = f"s3://{INGESTOR_S3_BUCKET}/node_remover/deleted_ids/{transformer_type}.parquet"

    try:
        df = df_from_s3_parquet(s3_file_uri)
    except OSError:
        print("File storing previously deleted ids not found. This should only happen on the first run.")
        df = pl.DataFrame(schema=DELETED_IDS_LOG_SCHEMA)
  
    ids_with_timestamp = {
        "timestamp": [datetime.now()] * len(ids),
        "id": list(ids)
    }
    new_data = pl.DataFrame(ids_with_timestamp, schema=DELETED_IDS_LOG_SCHEMA)
    
    df = pl.concat([df, new_data], how="vertical")
    df_to_s3_parquet(df, s3_file_uri)
        
    
def handler(transformer_type: TransformerType, is_local = False):
    try:
        # Retrieve a list of all node ids which were loaded into the graph as part of the previous run  
        previous_ids = get_previous_node_ids(transformer_type)
    except OSError:
        print("File storing previously bulk loaded ids not found. This should only happen on the first run.")
        previous_ids = set()
        
    # Retrieve a list of node ids which were loaded into the graph as part of the current run
    current_ids = get_current_node_ids(transformer_type)
    
    # IDs which were removed from the transformer CSV output since the last time we ran the node remover 
    deleted_ids = previous_ids.difference(current_ids)
    print(f"Removed {len(deleted_ids)} {transformer_type} ids since the last run.")
    
    # Delete the corresponding nodes from the graph
    client = get_neptune_client(is_local)
    client.delete_nodes_with_ids(list(deleted_ids))
    
    # Add ids which were deleted as part of this run to a log file storing all previously deleted ids
    log_deleted_ids(transformer_type, deleted_ids)

    # Update the list of all ids which have been loaded into the graph
    update_node_ids(transformer_type, current_ids)
    print("Successfully updated bulk loaded ids.")


def lambda_handler(event: dict, context: typing.Any):
    transformer_type = event["transformer_type"]
    handler(transformer_type)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(TransformerType),
        help="Which transformer's output to bulk load.",
        required=True,
    )
    args = parser.parse_args()

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
