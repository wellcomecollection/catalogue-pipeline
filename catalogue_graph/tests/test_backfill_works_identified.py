import json
from typing import Any
from unittest.mock import MagicMock

import pyarrow as pa
import pytest

from id_minter.schemata import WORKS_IDENTIFIED_ICEBERG_SCHEMA
from scripts.backfill_works_identified import (
    BackfillError,
    backfill,
    existing_versions,
    iter_documents,
    to_document,
)

VERSION = 1727206010000  # 2024-09-24T19:26:50Z in milliseconds


def work(canonical_id: str, modified: str = "2024-09-24T19:26:50Z") -> dict[str, Any]:
    return {
        "type": "Visible",
        "state": {
            "canonicalId": canonical_id,
            "sourceIdentifier": {
                "identifierType": {"id": "sierra-system-number"},
                "value": f"b{canonical_id}",
                "ontologyType": "Work",
            },
            "sourceModifiedTime": modified,
            "mergeCandidates": [{"id": {"canonicalId": "efgh5678"}}],
        },
        "data": {"title": "Test record"},
    }


def es_client(*pages: list[dict[str, Any]]) -> MagicMock:
    """A client whose search returns the given pages of documents, then an empty page."""
    client = MagicMock()
    client.open_point_in_time.return_value = {"id": "pit-1"}
    responses = [
        {
            "pit_id": "pit-1",
            "hits": {
                "hits": [{"_source": doc, "sort": [i]} for i, doc in enumerate(page)]
            },
        }
        for page in pages
    ]
    responses.append({"pit_id": "pit-1", "hits": {"hits": []}})
    client.search.side_effect = responses
    return client


def table(*append_outcomes: Exception | None) -> MagicMock:
    t = MagicMock()
    t.schema.return_value = WORKS_IDENTIFIED_ICEBERG_SCHEMA
    t.append.side_effect = append_outcomes or [None]
    return t


class TestIterDocuments:
    def test_pages_through_a_point_in_time_and_closes_it(self) -> None:
        client = es_client([work("a1"), work("a2")], [work("a3")])

        docs = list(iter_documents(client, "works-identified-test"))

        assert [d["state"]["canonicalId"] for d in docs] == ["a1", "a2", "a3"]
        second_call = client.search.call_args_list[1].kwargs["body"]
        assert second_call["search_after"] == [1]
        assert second_call["pit"]["id"] == "pit-1"
        client.close_point_in_time.assert_called_once_with(id="pit-1")

    def test_limit_stops_early_sizes_the_page_and_still_closes(self) -> None:
        client = es_client([work("a1"), work("a2"), work("a3")])

        docs = list(iter_documents(client, "works-identified-test", limit=2))

        assert len(docs) == 2
        assert client.search.call_args_list[0].kwargs["body"]["size"] == 2
        client.close_point_in_time.assert_called_once()


class TestToDocument:
    def test_versions_on_source_modified_time_like_the_minter(self) -> None:
        document = to_document(work("a1"))

        assert document.target_id == "a1"
        assert document.source_id == "sierra-system-number/ba1"
        assert document.version == VERSION


class TestExistingVersions:
    def test_keeps_the_newest_version_per_work(self) -> None:
        t = MagicMock()
        t.scan.return_value.to_arrow.return_value = pa.table(
            {"id": ["a1", "a1", "a2"], "version": [5, 7, 3]}
        )

        assert existing_versions(t) == {"a1": 7, "a2": 3}
        t.scan.assert_called_once_with(selected_fields=("id", "version"))


class TestBackfill:
    def test_appends_every_document_as_a_row(self) -> None:
        client = es_client([work("a1"), work("a2")])
        t = table()

        written, skipped = backfill(client, "works-identified-test", lambda: t, {})

        assert (written, skipped) == (2, 0)
        rows = t.append.call_args[0][0].to_pylist()
        assert [r["id"] for r in rows] == ["a1", "a2"]
        assert rows[0]["version"] == VERSION
        assert rows[0]["merge_candidate_ids"] == ["efgh5678"]
        assert json.loads(rows[0]["content"])["data"]["title"] == "Test record"

    def test_skips_works_the_table_holds_at_that_version_or_newer(self) -> None:
        client = es_client([work("a1"), work("a2"), work("a3")])
        t = table()
        existing = {"a1": VERSION, "a2": VERSION + 1, "a3": VERSION - 1}

        written, skipped = backfill(
            client, "works-identified-test", lambda: t, existing
        )

        assert (written, skipped) == (1, 2)
        assert [r["id"] for r in t.append.call_args[0][0].to_pylist()] == ["a3"]

    def test_reloads_the_table_for_each_append(self) -> None:
        client = es_client([work("a1")])
        load_table = MagicMock(return_value=table())

        backfill(client, "works-identified-test", load_table, {})

        load_table.assert_called_once()

    def test_a_failed_append_stops_the_run(self) -> None:
        client = es_client([work("a1")])
        t = table(RuntimeError("no"))

        with pytest.raises(BackfillError, match="after 0 rows"):
            backfill(client, "works-identified-test", lambda: t, {})

    def test_nothing_to_write(self) -> None:
        t = table()

        assert backfill(es_client(), "works-identified-test", lambda: t, {}) == (
            0,
            0,
        )
        t.append.assert_not_called()
