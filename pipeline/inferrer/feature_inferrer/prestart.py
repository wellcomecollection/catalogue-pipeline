import os

from botocore.exceptions import ClientError

from weco_datascience.aws import download_object_from_s3
from weco_datascience.logging import get_logger

logger = get_logger("prestart")

try:
    logger.info("Fetching pretrained LSHEncoder model")
    bucket = os.environ["MODEL_DATA_BUCKET"]
    key = os.environ["MODEL_OBJECT_KEY"]
    download_object_from_s3(
        bucket_name=bucket,
        object_key=key,
        file_name=os.path.join("app/data", os.path.basename(key)),
    )
    logger.info("Fetched pretrained LSHEncoder model")
except KeyError:
    logger.info("Skipping model fetch, assuming one exists locally.")
except ClientError as e:
    logger.error(f"Failed to fetch pretrained LSHEncoder: {e}")
    raise
