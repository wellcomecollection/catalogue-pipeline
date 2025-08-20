"""
Transformer step for EBSCO adapter pipeline.

This module transforms data from the loader output into the final format
for downstream processing.
"""

import argparse
from typing import Any

from pydantic import BaseModel

import config
from table_config import get_glue_table, get_local_table
from utils.iceberg import IcebergTableClient


class EbscoAdapterTransformerConfig(BaseModel):
    use_glue_table: bool = True


class EbscoAdapterTransformerEvent(BaseModel):
    changeset_id: str | None = None


class EbscoAdapterTransformerResult(BaseModel):
    records_transformed: int = 0


def handler(
    event: EbscoAdapterTransformerEvent, config_obj: EbscoAdapterTransformerConfig
) -> EbscoAdapterTransformerResult:
    """
    Main handler for the transformer step.

    Takes output from the loader and performs transformation operations.
    Currently implements a no-op for skeleton purposes.

    Args:
        event: Event containing loader output (snapshot_id)
        config_obj: Configuration object

    Returns:
        EbscoAdapterTransformerResult containing transformation results
    """
    print(f"Running transformer handler with config: {config_obj}")
    print(f"Processing event: {event}")

    try:
        changeset_id = event.changeset_id
        print(f"Processing loader output with changeset_id: {changeset_id}")

        # Initialize to default value in case changeset_id is None or no records are found
        records_transformed: int = 0

        # TODO: Implement actual transformation logic here
        # For now, this is a no-op that processes the changeset

        if changeset_id:
            print(f"Transform data from changeset: {changeset_id}")

            if config_obj.use_glue_table:
                print("Using AWS Glue table...")
                table = get_glue_table(
                    s3_tables_bucket=config.S3_TABLES_BUCKET,
                    table_name=config.GLUE_TABLE_NAME,
                    namespace=config.GLUE_NAMESPACE,
                    region=config.AWS_REGION,
                    account_id=config.AWS_ACCOUNT_ID,
                )
            else:
                print("Using local table...")
                table = get_local_table(
                    table_name=config.LOCAL_TABLE_NAME,
                    namespace=config.LOCAL_NAMESPACE,
                    db_name=config.LOCAL_DB_NAME,
                )

            table_client = IcebergTableClient(table)

            df = table_client.get_records_by_changeset(changeset_id)

            # In a real implementation, we would:
            # 1. Read data from the Iceberg table using the snapshot_id
            # 2. Apply transformations to the records
            # 3. Write transformed data to output location
            records_transformed = len(df)

        result = EbscoAdapterTransformerResult(records_transformed=records_transformed)

        print(f"Transformer completed successfully: {result}")
        return result

    except Exception as e:
        print(f"Error processing event: {e}")
        return EbscoAdapterTransformerResult()


def lambda_handler(event: EbscoAdapterTransformerEvent, context: Any) -> dict[str, Any]:
    """
    Lambda handler for the transformer step.

    Args:
        event: Lambda event containing loader output
        context: Lambda context object

    Returns:
        Dictionary containing transformation results
    """
    try:
        return handler(
            EbscoAdapterTransformerEvent.model_validate(event),
            EbscoAdapterTransformerConfig(),
        ).model_dump()
    except Exception as e:
        print(f"Lambda handler error: {e}")
        return EbscoAdapterTransformerResult().model_dump()


def local_handler() -> EbscoAdapterTransformerResult:
    """Handle local execution with command line arguments."""
    parser = argparse.ArgumentParser(description="Transform EBSCO adapter data")
    parser.add_argument(
        "--changeset-id", type=str, help="Changeset ID from loader output to transform"
    )
    parser.add_argument(
        "--use-glue-table",
        action="store_true",
        help="Use AWS Glue table instead of local table",
    )

    args = parser.parse_args()

    event = EbscoAdapterTransformerEvent(changeset_id=args.changeset_id)
    config_obj = EbscoAdapterTransformerConfig(use_glue_table=args.use_glue_table)

    return handler(event=event, config_obj=config_obj)


def main() -> None:
    """Entry point for the transformer script"""
    print("Running local transformer handler...")
    result = local_handler()
    print(f"Result: {result}")
    # Since we don't have a status field anymore, we'll assume success
    # unless there's an exception (which would be caught in handler)


if __name__ == "__main__":
    main()
