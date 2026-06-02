import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

TEST_DOCUMENTS_DIR = Path(__file__).resolve().parent / "test_documents"


def save_document(
    doc_id: str, description: str, record_id: str, document: dict[str, Any]
) -> None:
    TEST_DOCUMENTS_DIR.mkdir(parents=True, exist_ok=True)
    path = TEST_DOCUMENTS_DIR / f"{doc_id}.json"

    output = {
        "description": description,
        "id": record_id,
        "document": document,
    }

    # Only write if content has changed (ignore createdAt)
    if path.exists():
        existing = json.loads(path.read_text())
        existing.pop("createdAt", None)
        if existing == output:
            return

    output["createdAt"] = datetime.now(UTC).isoformat()
    path.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n")
