import config
from data_validation.concept_types import get_concepts_with_inconsistent_types
from utils.aws import write_csv_to_s3

S3_DATA_QUALITY_CHECKS_PREFIX = f"s3://{config.INGESTOR_S3_BUCKET}/data_quality_checks"


def save_data_quality_check_result(logged_items: list[dict], name: str):
    write_csv_to_s3(f"{S3_DATA_QUALITY_CHECKS_PREFIX}/{name}.csv", list(logged_items))


if __name__ == "__main__":
    invalid_items = get_concepts_with_inconsistent_types()
    save_data_quality_check_result(invalid_items, "inconsistent_concept_types")
