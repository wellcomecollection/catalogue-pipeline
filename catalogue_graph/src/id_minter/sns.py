"""Publish minted canonical IDs to a downstream SNS topic.

Each ID is sent as a plain string message so the Scala matcher
(consuming via SNS → SQS) receives the work ID directly.

Batches are published in parallel using a thread pool to speed up
large runs.
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed

import boto3
import structlog

from utils.aws import publish_batch_to_sns

logger = structlog.get_logger(__name__)

# AWS SNS publish_batch accepts at most 10 entries.
SNS_BATCH_SIZE = 10

# How many publish_batch calls to run concurrently.
SNS_MAX_WORKERS = 50


def publish_ids_to_sns(
    topic_arn: str,
    ids: list[str],
    max_workers: int = SNS_MAX_WORKERS,
) -> None:
    """Publish canonical IDs to SNS in parallel batches of 10.

    Raises on the first batch failure so the step function can retry.
    """
    total = len(ids)
    batches = [ids[i : i + SNS_BATCH_SIZE] for i in range(0, total, SNS_BATCH_SIZE)]
    num_batches = len(batches)

    if num_batches == 0:
        return

    logger.info(
        "Publishing IDs to SNS",
        total_ids=total,
        num_batches=num_batches,
        topic_arn=topic_arn,
    )

    published = 0
    sns_client = boto3.Session().client("sns")
    with ThreadPoolExecutor(max_workers=min(max_workers, num_batches)) as pool:
        futures = {
            pool.submit(publish_batch_to_sns, topic_arn, batch, sns_client): idx
            for idx, batch in enumerate(batches)
        }
        for future in as_completed(futures):
            future.result()  # raises on failure
            published += 1
            if published % 100 == 0 or published == num_batches:
                logger.info(
                    "SNS publish progress",
                    batches_published=published,
                    num_batches=num_batches,
                    ids_published=min(published * SNS_BATCH_SIZE, total),
                    total_ids=total,
                )

    logger.info(
        "Finished publishing IDs to SNS",
        total_ids=total,
        num_batches=num_batches,
        topic_arn=topic_arn,
    )
