"""Tests for the transformer Lambda's handling of find_work partition refs."""

from typing import Any

import pytest

from adapters.steps import transformer
from adapters.steps.transformer import TransformerEvent, TransformerResult
from utils.aws import pydantic_to_s3_json


class _LambdaContext:
    aws_request_id = "req-abc123"


def _fake_handler(received: list[TransformerEvent]) -> Any:
    def handler(
        event: TransformerEvent, *args: Any, **kwargs: Any
    ) -> TransformerResult:
        received.append(event)
        return TransformerResult(
            **event.model_dump(),
            success_count=1,
            failure_count=0,
            unmatched_count=0,
            report_s3_uri="s3://test-bucket/report.json",
        )

    return handler


def test_lambda_handler_resolves_a_partition_ref_from_s3(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    partition = TransformerEvent(
        transformer_type="ebsco",
        job_id="job-1-p000",
        changeset_ids=["changeset-1", "changeset-2"],
        snapshot_id=123,
    )
    s3_uri = "s3://test-bucket/pipeline-dev/ebsco/dev/find_work/job-1/partition-0.json"
    pydantic_to_s3_json(partition, s3_uri)
    received: list[TransformerEvent] = []
    monkeypatch.setattr(transformer, "handler", _fake_handler(received))

    output = transformer.lambda_handler({"s3_uri": s3_uri}, _LambdaContext())

    assert received == [partition]
    assert output["job_id"] == "job-1-p000"
    assert output["s3_uri"] == s3_uri
    assert "changeset_ids" not in output


def test_lambda_handler_still_accepts_an_inline_event(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    received: list[TransformerEvent] = []
    monkeypatch.setattr(transformer, "handler", _fake_handler(received))

    transformer.lambda_handler(
        {"transformer_type": "axiell", "job_id": "job-1", "changeset_ids": ["cs"]},
        _LambdaContext(),
    )

    assert received[0].changeset_ids == ["cs"]
