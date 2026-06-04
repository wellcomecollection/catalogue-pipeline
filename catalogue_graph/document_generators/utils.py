import json
from collections.abc import Sequence
from datetime import UTC, datetime
from pathlib import Path

from ingestor.models.indexable.record import IndexableRecord

TEST_DOCUMENTS_DIR = Path(__file__).resolve().parent / "test_documents"


def save_document(doc_id: str, description: str, document: IndexableRecord) -> None:
    TEST_DOCUMENTS_DIR.mkdir(parents=True, exist_ok=True)
    path = TEST_DOCUMENTS_DIR / f"{doc_id}.json"

    output = {
        "description": description,
        "id": document.get_id(),
        "document": document.model_dump(mode="json", exclude_none=True),
    }

    # Only write if content has changed (ignore createdAt)
    if path.exists():
        existing = json.loads(path.read_text())
        existing.pop("createdAt", None)
        if existing == output:
            return

    output["createdAt"] = datetime.now(UTC).isoformat()
    path.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n")


def save_documents(
    documents: Sequence[IndexableRecord], description: str, doc_id: str
) -> None:
    if len(documents) == 1:
        save_document(doc_id, description, documents[0])
    else:
        for index, doc in enumerate(documents):
            save_document(f"{doc_id}.{index}", description, doc)
