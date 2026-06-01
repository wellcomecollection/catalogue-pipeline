"""
Updates image test documents from the live pipeline-storage Elasticsearch index.

Fetches documents whose IDs match certain prefixes (color-filter-tests, similar-features)
from the images-indexed index and writes them into the test_documents directory.

Run from the catalogue_graph directory:
    uv run python -m document_generators.update_image_documents <pipeline_date>
"""

import json
from datetime import UTC, datetime
from pathlib import Path

import click
import elasticsearch
import structlog

from utils.elasticsearch import get_client

logger = structlog.get_logger(__name__)

TEST_DOCUMENTS_DIR = Path(__file__).resolve().parent / "test_documents"

IMAGE_DOCUMENT_PREFIXES = [
    "images.examples.color-filter-tests",
    "images.similar-features",
]


@click.command()
@click.argument("pipeline_date")
def main(pipeline_date: str) -> None:
    es_client = get_client(
        api_key_name="read_only", pipeline_date=pipeline_date, es_mode="public"
    )
    index_name = f"images-indexed-{pipeline_date}"

    for child in TEST_DOCUMENTS_DIR.iterdir():
        if not child.is_file():
            continue
        if not any(child.name.startswith(prefix) for prefix in IMAGE_DOCUMENT_PREFIXES):
            continue

        test_document = json.loads(child.read_text())

        try:
            response = es_client.get(index=index_name, id=test_document["id"])
            pipeline_doc = response["_source"]
        except elasticsearch.NotFoundError:
            logger.warning(
                "Document not found in index",
                id=test_document["id"],
                file=child.name,
                pipeline_date=pipeline_date,
            )
            continue

        if json.dumps(test_document["document"]) == json.dumps(pipeline_doc):
            logger.info("No changes", file=child.name)
            continue

        test_document["document"] = pipeline_doc
        test_document["createdAt"] = datetime.now(UTC).isoformat()

        child.write_text(json.dumps(test_document, indent=2, ensure_ascii=False) + "\n")
        logger.info("Updated", file=child.name)


if __name__ == "__main__":
    main()
