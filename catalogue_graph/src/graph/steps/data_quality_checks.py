import argparse

import structlog

import config
from graph.data_validation.concept_types import get_concepts_with_inconsistent_types
from utils.argparse import add_pipeline_event_args
from utils.aws import write_csv_to_s3
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def save_data_quality_check_result(
    logged_items: list[dict], name: str, graph_date: str
) -> None:
    bucket = config.CATALOGUE_GRAPH_S3_BUCKET
    graph_prefix = f"graph-{graph_date}/" if graph_date else ""
    prefix = f"s3://{bucket}/{graph_prefix}data_quality_checks"
    write_csv_to_s3(f"{prefix}/{name}.csv", list(logged_items))

    logger.info(
        "Data quality check result saved",
        name=name,
        item_count=len(logged_items),
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(parser, {"graph_date"})

    args = parser.parse_args()

    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(),
            pipeline_step="data_quality_checks",
        )
    )

    invalid_items = get_concepts_with_inconsistent_types(graph_date=args.graph_date)
    save_data_quality_check_result(
        list(invalid_items), "inconsistent_concept_types", graph_date=args.graph_date
    )


if __name__ == "__main__":
    main()
