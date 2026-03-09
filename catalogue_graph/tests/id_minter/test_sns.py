"""Unit tests for id_minter.sns — SNS publishing of minted IDs."""

from __future__ import annotations

import json

import pytest

from id_minter.sns import publish_ids_to_sns
from tests.mocks import MockSNSClient

TEST_TOPIC = "arn:aws:sns:eu-west-1:123456789:test-topic"


def _published_ids() -> set[str]:
    """Extract all canonical IDs from MockSNSClient.publish_batch_request_entries."""
    ids: set[str] = set()
    for call in MockSNSClient.publish_batch_request_entries:
        for entry in call["PublishBatchRequestEntries"]:
            msg = json.loads(entry["Message"])
            ids.add(msg["default"])
    return ids


class TestPublishIdsToSns:
    def test_single_id(self) -> None:
        publish_ids_to_sns(TEST_TOPIC, ["abc12345"])

        assert len(MockSNSClient.publish_batch_request_entries) == 1
        call = MockSNSClient.publish_batch_request_entries[0]
        assert call["TopicArn"] == TEST_TOPIC
        entries = call["PublishBatchRequestEntries"]
        assert len(entries) == 1
        assert json.loads(entries[0]["Message"]) == {"default": "abc12345"}

    def test_batches_of_10(self) -> None:
        ids = [f"id{i:04d}" for i in range(25)]
        publish_ids_to_sns(TEST_TOPIC, ids)

        calls = MockSNSClient.publish_batch_request_entries
        assert len(calls) == 3  # 10 + 10 + 5
        batch_sizes = sorted(len(c["PublishBatchRequestEntries"]) for c in calls)
        assert batch_sizes == [5, 10, 10]
        assert _published_ids() == set(ids)

    def test_exactly_10(self) -> None:
        ids = [f"id{i:04d}" for i in range(10)]
        publish_ids_to_sns(TEST_TOPIC, ids)

        assert len(MockSNSClient.publish_batch_request_entries) == 1
        assert _published_ids() == set(ids)

    def test_empty_list(self) -> None:
        publish_ids_to_sns(TEST_TOPIC, [])
        assert len(MockSNSClient.publish_batch_request_entries) == 0

    def test_messages_are_plain_strings(self) -> None:
        """Each message is a plain canonical ID string, not JSON-encoded."""
        publish_ids_to_sns(TEST_TOPIC, ["workid01"])

        entry = MockSNSClient.publish_batch_request_entries[0][
            "PublishBatchRequestEntries"
        ][0]
        msg = json.loads(entry["Message"])
        # The "default" value should be the raw ID string
        assert msg == {"default": "workid01"}
        assert entry["MessageStructure"] == "json"

    def test_parallel_publishing(self) -> None:
        """Large batches are published in parallel (verified by result correctness)."""
        ids = [f"p{i:06d}" for i in range(100)]
        publish_ids_to_sns(TEST_TOPIC, ids, max_workers=4)

        assert len(MockSNSClient.publish_batch_request_entries) == 10
        assert _published_ids() == set(ids)

    def test_failure_propagates(self) -> None:
        """An SNS publish error raises so the step function can retry."""
        original = MockSNSClient.publish_batch

        def boom(*args, **kwargs):  # type: ignore[no-untyped-def]
            raise RuntimeError("SNS unavailable")

        MockSNSClient.publish_batch = staticmethod(boom)  # type: ignore[assignment]
        try:
            with pytest.raises(RuntimeError, match="SNS unavailable"):
                publish_ids_to_sns(TEST_TOPIC, ["fail0001"])
        finally:
            MockSNSClient.publish_batch = original  # type: ignore[assignment]
