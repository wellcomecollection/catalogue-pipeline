import argparse
from typing import Any

from pydantic import BaseModel

from steps.loader import EbscoAdapterLoaderEvent


class EbscoAdapterTriggerConfig(BaseModel):
    is_local: bool = False


class EbscoAdapterTriggerEvent(BaseModel):
    job_id: str | None = None


def handler(
    event: EbscoAdapterTriggerEvent, config: EbscoAdapterTriggerConfig
) -> EbscoAdapterLoaderEvent:
    print(f"Running handler with config: {config}")
    print(f"Processing event: {event}")
    return EbscoAdapterLoaderEvent(s3_location="s3://bucket/path/to/file")


def lambda_handler(event: EbscoAdapterTriggerEvent, context: Any) -> dict[str, Any]:
    return handler(
        EbscoAdapterTriggerEvent.model_validate(event), EbscoAdapterTriggerConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process.",
        required=False,
    )
    parser.add_argument(
        "--local",
        action="store_true",
        help="Run locally without AWS dependencies",
    )

    args = parser.parse_args()

    event = EbscoAdapterTriggerEvent(job_id=args.job_id)
    config = EbscoAdapterTriggerConfig(is_local=args.local)

    handler(event=event, config=config)


def main() -> None:
    """Entry point for the trigger script"""
    print("Running local handler...")
    local_handler()


if __name__ == "__main__":
    main()
