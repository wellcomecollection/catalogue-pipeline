import structlog

import config
from data_validation.concept_types import get_concepts_with_inconsistent_types
from utils.aws import write_csv_to_s3
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

S3_DATA_QUALITY_CHECKS_PREFIX = (
    f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/data_quality_checks"
)


def save_data_quality_check_result(logged_items: list[dict], name: str) -> None:
    write_csv_to_s3(f"{S3_DATA_QUALITY_CHECKS_PREFIX}/{name}.csv", list(logged_items))
    logger.info(
        "Data quality check result saved",
        name=name,
        item_count=len(logged_items),
    )


if __name__ == "__main__":
    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(),
            pipeline_step="data_quality_checks",
        )
    )

    invalid_items = get_concepts_with_inconsistent_types()
    save_data_quality_check_result(list(invalid_items), "inconsistent_concept_types")
