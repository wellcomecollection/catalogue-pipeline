import argparse
from typing import Any, Optional
from pydantic import BaseModel

class EbscoAdapterTriggerConfig(BaseModel):
    is_local: bool = False

class EbscoAdapterTriggerEvent(BaseModel):
    foo: str | None = None

def handler(event: EbscoAdapterTriggerEvent, config: EbscoAdapterTriggerConfig) -> None:
    return None

def lambda_handler(event: EbscoAdapterTriggerEvent, context: Any) -> dict:
    return handler(
        EbscoAdapterTriggerEvent.model_validate(event), EbscoAdapterTriggerConfig()
    ).model_dump()

def local_handler() -> Optional[str]:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument("xmlfile", help="Path to the XML file to process")
    parser.add_argument(
        "--local",
        action="store_true",
        help="Run locally without AWS dependencies",
    )

    args = parser.parse_args()

    return handler(local=args.local)


if __name__ == "__main__":
    print("Running local handler...")
    local_handler()
