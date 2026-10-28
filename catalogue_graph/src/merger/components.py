"""The components the previous run resolved, and the diff against the current ones."""

import polars as pl
import structlog
from botocore.exceptions import ClientError

from utils.aws import df_from_s3_parquet

logger = structlog.get_logger(__name__)

COMPONENTS_SCHEMA: dict = {
    "id": pl.Utf8,
    "component_id": pl.Utf8,
    "last_modified": pl.Datetime("us", "UTC"),
}


def read_previous_components(s3_uri: str) -> pl.DataFrame:
    try:
        return df_from_s3_parquet(s3_uri).select(COMPONENTS_SCHEMA.keys())
    except OSError as error:
        if not _is_missing_key(error):
            raise
        logger.info("No previous components found, treating every component as changed")
        return pl.DataFrame(schema=COMPONENTS_SCHEMA)


def _is_missing_key(error: OSError) -> bool:
    """smart_open raises its error from the S3 client's; only a missing key means a first run."""
    cause = error.__cause__
    return (
        isinstance(cause, ClientError)
        and cause.response["Error"]["Code"] == "NoSuchKey"
    )


def find_changed_components(
    current: pl.DataFrame, previous: pl.DataFrame
) -> pl.DataFrame:
    """Every work in a component whose members, or their write times, differ from the previous run."""
    changed = (
        _signatures(current)
        .join(_signatures(previous), on="component_id", how="left", suffix="_previous")
        .filter(
            pl.col("signature_previous").is_null()
            | (pl.col("signature") != pl.col("signature_previous"))
        )
        .select("component_id")
    )
    changed_components = current.join(changed, on="component_id", how="semi")

    logger.info(
        "Found changed components",
        changed_components=changed.height,
        changed_works=changed_components.height,
    )
    return changed_components


def _signatures(components: pl.DataFrame) -> pl.DataFrame:
    written = pl.col("last_modified").cast(pl.Datetime("us", "UTC")).dt.epoch("us")
    member = pl.concat_str([pl.col("id"), written], separator=":")
    return components.group_by("component_id").agg(
        member.sort().str.join(",").alias("signature")
    )
