#!/usr/bin/env python
"""Image inference manager (Python port of the Scala `inference_manager`).

Runs as an ECS task launched by the image-inferrer state machine. Given a
partition of image ids, it retrieves the `Image[Initial]` documents, downloads
the IIIF thumbnail for each, calls the three inferrer sidecars over localhost,
assembles `InferredData`, and bulk-writes `Image[Augmented]` documents to the
`images-augmented` index. An image is only indexed if every inferrer responds
(parity with the Scala "must receive all three responses" behaviour).
"""

from __future__ import annotations

import json
import os
from argparse import ArgumentParser
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime

import structlog
from elasticsearch import Elasticsearch

from inferrer.adapters import FEATURE_VECTOR_SIZE, INFERRERS, call_inferrer
from inferrer.image_downloader import (
    delete_image,
    download_image,
    file_url,
)
from inferrer.models import (
    AugmentedImageStateToIndex,
    AugmentedImageToIndex,
    InferenceManagerEvent,
    InferenceManagerResult,
    InitialImage,
)
from ingestor.models.augmented.image import InferredData
from utils.argparse import add_pipeline_event_args, validate_es_mode_for_writes
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
    get_images_augmented_index_name,
    get_images_initial_index_name,
    index_es_batch,
)
from utils.logger import ExecutionContext, setup_logging

logger = structlog.get_logger(__name__)

# Number of images augmented concurrently; each image fans out to the three
# inferrers, so this also drives how full the sidecars' internal batch queues get.
IMAGE_PARALLELISM = int(os.environ.get("IMAGE_PARALLELISM", "10"))
# Per-request timeout (seconds) for both image download and inferrer calls.
REQUEST_TIMEOUT = float(os.environ.get("INFERRER_REQUEST_TIMEOUT_SECONDS", "30"))
IMAGES_ROOT = os.environ.get("IMAGES_ROOT", "/data")


class PoisonedImageError(Exception):
    """Raised when inference would produce an incomplete (poisoned) augmented doc.

    We fail the task rather than indexing a document with empty features, an
    empty palette, or a missing aspect ratio / average colour.
    """


def validate_inferred(image_id: str, inferred: InferredData) -> None:
    problems = []
    if len(inferred.features) != FEATURE_VECTOR_SIZE:
        problems.append(
            f"feature vector has {len(inferred.features)} dimensions, "
            f"expected {FEATURE_VECTOR_SIZE}"
        )
    if not inferred.palette_embedding:
        problems.append("palette embedding is empty")
    if inferred.average_color_hex is None:
        problems.append("average colour is missing")
    if inferred.aspect_ratio is None:
        problems.append("aspect ratio is missing")

    if problems:
        raise PoisonedImageError(
            f"Refusing to index poisoned augmented doc for image {image_id}: "
            + "; ".join(problems)
        )


def retrieve_initial_images(
    es_client: Elasticsearch, index_name: str, ids: list[str]
) -> list[InitialImage]:
    if not ids:
        return []

    response = es_client.mget(index=index_name, body={"ids": ids})
    images = []
    for doc in response["docs"]:
        if doc.get("found"):
            images.append(InitialImage.model_validate(doc["_source"]))
        else:
            logger.warning(
                "Initial image not found", image_id=doc.get("_id"), index=index_name
            )
    return images


def _build_augmented(
    image: InitialImage, inferred: InferredData
) -> AugmentedImageToIndex:
    state = AugmentedImageStateToIndex(
        canonical_id=image.state.canonical_id,
        source_identifier=image.state.source_identifier,
        inferred_data=inferred,
        augmented_time=datetime.now(UTC).isoformat(),
    )
    return AugmentedImageToIndex(
        state=state,
        source=image.source,
        locations=image.locations,
        version=image.version,
        modified_time=image.modified_time,
    )


def augment_image(image: InitialImage) -> AugmentedImageToIndex:
    """Download, infer, and assemble an augmented image.

    Raises if the image cannot be downloaded, any inferrer fails to respond, or
    the result would be a poisoned doc. The downloaded file is always cleaned up.
    """
    path = download_image(image, IMAGES_ROOT, REQUEST_TIMEOUT)
    try:
        partial: dict = {"features": [], "palette_embedding": []}
        url = file_url(path)
        with ThreadPoolExecutor(max_workers=len(INFERRERS)) as pool:
            for result in pool.map(
                lambda inf: call_inferrer(inf, url, REQUEST_TIMEOUT),
                INFERRERS,
            ):
                partial.update(result)
        inferred = InferredData(**partial)
        validate_inferred(image.state.id(), inferred)
        return _build_augmented(image, inferred)
    finally:
        delete_image(path, IMAGES_ROOT)


def generate_operations(
    index_name: str, images: list[AugmentedImageToIndex]
) -> list[dict]:
    operations = []
    for image in images:
        source = json.loads(image.model_dump_json(exclude_none=True))
        # Version by modified time (epoch millis) so retries are idempotent,
        # mirroring the ingestor indexer.
        version = int(datetime.fromisoformat(image.modified_time).timestamp() * 1000)
        version = max(100, version)
        operations.append(
            {
                "_index": index_name,
                "_id": image.state.id(),
                "_source": source,
                "_version": version,
                "_version_type": "external_gte",
            }
        )
    return operations


def handler(
    event: InferenceManagerEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> InferenceManagerResult:
    setup_logging(execution_context)

    ids = event.ids or []
    logger.info(
        "Received event",
        pipeline_date=event.pipeline_date,
        image_count=len(ids),
    )

    es_client = get_client("inferrer", event.pipeline_date, es_mode)
    initial_index = get_images_initial_index_name(event)
    augmented_index = get_images_augmented_index_name(event)

    images = retrieve_initial_images(es_client, initial_index, ids)

    # All-or-nothing: if any image fails to download, fails an inferrer, or would
    # produce a poisoned doc, the exception propagates and fails the whole task
    # (the state machine then retries). We never index a partial/poisoned batch.
    with ThreadPoolExecutor(max_workers=IMAGE_PARALLELISM) as pool:
        augmented = list(pool.map(augment_image, images))

    operations = generate_operations(augmented_index, augmented)
    if operations:
        _, es_errors = index_es_batch(es_client, operations)
        if es_errors:
            logger.error(
                "Bulk indexing errors encountered",
                total_errors=len(es_errors),
                first_errors=es_errors[:5],
            )
            raise RuntimeError(f"Bulk indexing failed with {len(es_errors)} error(s)")

    logger.info(
        "Inference complete",
        processed=len(images),
        augmented=len(augmented),
    )
    return InferenceManagerResult(processed=len(images), augmented=len(augmented))


def event_validator(raw_input: str) -> InferenceManagerEvent:
    return InferenceManagerEvent.model_validate(json.loads(raw_input))


def local_handler(parser: ArgumentParser) -> None:
    add_pipeline_event_args(
        parser,
        {"pipeline_date", "index_date_augmented", "ids", "environment", "es_mode"},
    )
    args = parser.parse_args()
    validate_es_mode_for_writes(parser, args)
    event = InferenceManagerEvent.from_argparser(args)
    handler(event, es_mode=args.es_mode)


if __name__ == "__main__":
    from utils.steps import ecs_handler

    parser: ArgumentParser = ArgumentParser()
    parser.add_argument(
        "--use-cli",
        action="store_true",
        help="Whether to invoke the local CLI handler instead of the ECS handler.",
    )
    cli_args, _ = parser.parse_known_args()

    if cli_args.use_cli:
        local_handler(parser)
    else:
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=event_validator,
            pipeline_step="inference_manager",
        )
