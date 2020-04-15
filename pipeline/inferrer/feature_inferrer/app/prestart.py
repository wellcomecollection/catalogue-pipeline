import os

from botocore.exceptions import ClientError
from torchvision.models.vgg import vgg16

from src.aws import download_object_from_s3
from src.logging import get_logstash_logger

logger = get_logstash_logger("prestart")

try:
    logger.info("Fetching pretrained VGG16 model")
    feature_extractor = vgg16(pretrained=True, progress=False)
    logger.info("Fetched pretrained VGG model")
except Exception as e:
    logger.error(f"Failed to fetch pretrained VGG model: {e}")
    raise

try:
    logger.info("Fetching pretrained LSHEncoder model")
    bucket = os.environ["MODEL_DATA_BUCKET"]
    key = os.environ["MODEL_OBJECT_KEY"]
    download_object_from_s3(
        bucket_name=bucket,
        object_key=key,
        file_name=os.path.join("data", os.path.basename(key)),
    )
    logger.info("Fetched pretrained LSHEncoder model")
except KeyError:
    logger.info("Skipping model fetch, assuming one exists locally.")
except ClientError as e:
    logger.error(f"Failed to fetch pretrained LSHEncoder: {e}")
    raise
