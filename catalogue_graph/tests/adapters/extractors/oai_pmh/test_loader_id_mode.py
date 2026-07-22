"""Tests for the loader step's id mode."""

from __future__ import annotations

from datetime import UTC, datetime
from unittest.mock import MagicMock, patch

import httpx
import pyarrow as pa
import pytest
from lxml import etree
from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import IdDoesNotExistError, OAIError
from oai_pmh_client.models import Header, Record

from adapters.extractors.oai_pmh.models.step_events import (
    DEFAULT_ID_COMMIT_EVERY,
    MAX_IDS_PER_RUN,
    UNFETCHABLE_SAMPLE_SIZE,
    OAIPMHIdLoaderEvent,
    OAIPMHIdLoaderResponse,
    OAIPMHLoaderEvent,
    validate_loader_event,
)
from adapters.extractors.oai_pmh.reporting import OAIPMHIdLoadReport
from adapters.steps.oai_pmh import loader
from adapters.steps.oai_pmh.loader import LoaderRuntime
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_store import WindowStore

CONFIGURED_PREFIX = "oai_marcxml"


def _empty_body_error() -> etree.XMLSyntaxError:
    """A real XMLSyntaxError, as parsing an empty OAI body raises."""
    try:
        etree.fromstring(b"")
    except etree.XMLSyntaxError as exc:
        return exc
    raise AssertionError("parsing empty content should have raised")


def _record(identifier: str) -> Record:
    header = Header(
        identifier=identifier,
        datestamp=datetime(2025, 1, 1, tzinfo=UTC),
        setSpec=["collect"],
        status=False,
    )
    metadata = etree.fromstring(f"<meta><id>{identifier}</id></meta>")
    return Record(header=header, metadata=metadata)


def _event(ids: list[str], **overrides: object) -> OAIPMHIdLoaderEvent:
    payload: dict[str, object] = {
        "adapter_type": "axiell",
        "job_id": "idload-20250101T0000",
        "ids": ids,
        "polite_delay_seconds": 0,
    }
    payload.update(overrides)
    return OAIPMHIdLoaderEvent.model_validate(payload)


def _runtime(get_record: object) -> tuple[LoaderRuntime, list[pa.Table]]:
    oai = MagicMock(spec=OAIClient)
    oai.get_record.side_effect = get_record

    store = MagicMock(spec=AdapterStore)
    commits: list[pa.Table] = []

    def _update(table: pa.Table) -> MagicMock:
        commits.append(table)
        return MagicMock(
            changeset_id=f"cs{len(commits)}",
            upserted_record_ids=table["id"].to_pylist(),
        )

    store.incremental_update.side_effect = _update

    runtime = LoaderRuntime(
        table_client=store,
        oai_client=oai,
        adapter_namespace="axiell",
        adapter_name="axiell",
        oai_metadata_prefix=CONFIGURED_PREFIX,
    )
    return runtime, commits


class TestIdLoaderEvent:
    def test_parses_a_minimal_event(self) -> None:
        event = OAIPMHIdLoaderEvent.model_validate(
            {
                "adapter_type": "axiell",
                "job_id": "j1",
                "ids": ["collect:1"],
            }
        )
        assert event.ids == ["collect:1"]
        assert event.commit_every == DEFAULT_ID_COMMIT_EVERY

    def test_rejects_a_run_over_the_id_ceiling(self) -> None:
        with pytest.raises(ValueError, match="exceeds the"):
            _event([f"collect:{i}" for i in range(MAX_IDS_PER_RUN + 1)])

    @pytest.mark.parametrize("commit_every", [0, -1])
    def test_rejects_a_non_positive_commit_every(self, commit_every: int) -> None:
        # 0 would disable auto-flush and buffer the whole run; a negative value
        # is truthy in the writer's flush check and commits one changeset per
        # record. Both are rejected at the event boundary rather than surprising
        # us mid-run.
        with pytest.raises(ValueError, match="greater than or equal to 1"):
            _event(["collect:1"], commit_every=commit_every)

    def test_rejects_a_negative_polite_delay(self) -> None:
        with pytest.raises(ValueError, match="greater than or equal to 0"):
            _event(["collect:1"], polite_delay_seconds=-0.1)

    def test_allows_a_zero_polite_delay(self) -> None:
        assert _event(["collect:1"], polite_delay_seconds=0).polite_delay_seconds == 0

    def test_field_presence_selects_the_model(self) -> None:
        """Only window mode has a ``window`` and only id mode has ``ids``, so
        the union needs no discriminator and stored trigger payloads written
        before id mode existed keep validating."""
        window_event = validate_loader_event(
            {
                "adapter_type": "axiell",
                "job_id": "j1",
                "window": {
                    "startTime": "2025-01-01T00:00:00Z",
                    "endTime": "2025-01-01T01:00:00Z",
                },
            }
        )
        assert isinstance(window_event, OAIPMHLoaderEvent)

        id_event = validate_loader_event(
            {
                "adapter_type": "axiell",
                "job_id": "j1",
                "ids": ["collect:1"],
            }
        )
        assert isinstance(id_event, OAIPMHIdLoaderEvent)


class TestExecuteIdLoader:
    def test_classifies_and_writes(self) -> None:
        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            if identifier == "collect:gone":
                raise IdDoesNotExistError("gone")
            if identifier == "collect:bad":
                raise _empty_body_error()
            return _record(identifier)

        runtime, commits = _runtime(get_record)
        response, outcome = loader.execute_id_loader(
            _event(["collect:1", "collect:gone", "collect:bad", "collect:2"]),
            runtime,
        )

        assert response.requested == 4
        assert response.recovered == 2
        assert response.removed == 1
        assert response.unfetchable_count == 1
        assert outcome.unfetchable == ["collect:bad"]
        assert len(commits) == 1
        assert commits[0]["id"].to_pylist() == ["collect:1", "collect:2"]

    def test_surfaces_changeset_ids_for_the_publish_gate(self) -> None:
        """The whole point: recovered records must reach the transformer."""
        runtime, _ = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )
        response, _ = loader.execute_id_loader(_event(["collect:1"]), runtime)

        assert response.changeset_ids == ["cs1"]
        assert response.changed_record_count == 1

    def test_accumulates_changeset_ids_across_commits(self) -> None:
        runtime, commits = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )
        response, _ = loader.execute_id_loader(
            _event(["collect:1", "collect:2", "collect:3"], commit_every=1),
            runtime,
        )

        assert len(commits) == 3
        assert response.changeset_ids == ["cs1", "cs2", "cs3"]

    def test_nothing_recoverable_yields_no_changesets(self) -> None:
        """An empty changeset list makes the state machine skip Publish event."""

        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            raise IdDoesNotExistError(identifier)

        runtime, commits = _runtime(get_record)
        response, _ = loader.execute_id_loader(
            _event(["collect:1", "collect:2"]), runtime
        )

        assert commits == []
        assert response.changeset_ids == []
        assert response.removed == 2

    def test_removed_ids_are_not_tombstoned(self) -> None:
        """idDoesNotExist is a weaker signal than an OAI deleted status, so it
        must not write a delete that would propagate downstream."""

        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            if identifier == "collect:gone":
                raise IdDoesNotExistError("gone")
            return _record(identifier)

        runtime, commits = _runtime(get_record)
        loader.execute_id_loader(_event(["collect:1", "collect:gone"]), runtime)

        written = commits[0].to_pylist()
        assert [r["id"] for r in written] == ["collect:1"]
        assert all(r["deleted"] is False for r in written)

    def test_dedupes_requested_ids(self) -> None:
        runtime, commits = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )
        response, _ = loader.execute_id_loader(
            _event(["collect:1", "collect:1", "collect:2"]), runtime
        )

        assert response.requested == 2
        assert commits[0]["id"].to_pylist() == ["collect:1", "collect:2"]

    @pytest.mark.parametrize(
        "error",
        [
            OAIError("protocol failure"),
            httpx.ConnectError("network down"),
            _empty_body_error(),
        ],
    )
    def test_transient_failures_classify_without_aborting(
        self, error: Exception
    ) -> None:
        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            if identifier == "collect:bad":
                raise error
            return _record(identifier)

        runtime, commits = _runtime(get_record)
        response, outcome = loader.execute_id_loader(
            _event(["collect:bad", "collect:2"]), runtime
        )

        assert outcome.unfetchable == ["collect:bad"]
        assert response.recovered == 1
        assert commits[0]["id"].to_pylist() == ["collect:2"]

    def test_unexpected_errors_propagate(self) -> None:
        """Bugs are deliberately not swallowed by the classification."""

        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            raise RuntimeError("a bug, not a flaky source")

        runtime, _ = _runtime(get_record)
        with pytest.raises(RuntimeError, match="a bug"):
            loader.execute_id_loader(_event(["collect:1"]), runtime)

    def test_unfetchable_sample_is_capped_but_count_is_exact(self) -> None:
        ids = [f"collect:{i}" for i in range(UNFETCHABLE_SAMPLE_SIZE + 25)]

        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            raise _empty_body_error()

        runtime, _ = _runtime(get_record)
        response, outcome = loader.execute_id_loader(_event(ids), runtime)

        assert response.unfetchable_count == len(ids)
        assert len(response.unfetchable_sample) == UNFETCHABLE_SAMPLE_SIZE
        assert len(outcome.unfetchable) == len(ids)

    def test_holds_no_window_store(self) -> None:
        """Id mode must not touch window state, or it would shift the trigger's
        resume cursor onto a range that was never harvested. It is not given a
        window store at all, so it cannot."""
        runtime, _ = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )
        loader.execute_id_loader(_event(["collect:1"]), runtime)

        assert runtime.store is None
        assert runtime.window_generator is None


class TestHandlerDispatch:
    def test_id_event_routes_to_id_mode_and_reports(self) -> None:
        runtime, _ = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )

        with patch(
            "adapters.steps.oai_pmh.loader.OAIPMHIdLoadReport.from_id_load"
        ) as mock_report:
            mock_report.return_value = MagicMock()
            response = loader.handler(_event(["collect:1"]), runtime=runtime)

        assert response.changeset_ids == ["cs1"]
        assert response.covered_window_keys == []
        mock_report.assert_called_once()
        # The report gets the full unfetchable list, not the response sample.
        assert "unfetchable" in mock_report.call_args.kwargs


class TestMetadataPrefix:
    """The event does not name a metadata prefix on any real path: the state
    machine's Pass state injects only mode and job_id, and the CLI has no flag
    for it. OAI-PMH requires metadataPrefix on every GetRecord, so the adapter's
    configured prefix has to be what actually goes out.
    """

    def _captured_prefix(self, event: OAIPMHIdLoaderEvent) -> str | None:
        seen: list[str | None] = []

        def get_record(*, identifier: str, metadata_prefix: str) -> Record:
            seen.append(metadata_prefix)
            return _record(identifier)

        runtime, _ = _runtime(get_record)
        loader.execute_id_loader(event, runtime)
        return seen[0]

    def test_falls_back_to_the_configured_prefix(self) -> None:
        event = _event(["collect:1"])
        assert event.metadata_prefix is None
        assert self._captured_prefix(event) == CONFIGURED_PREFIX

    def test_never_sends_none(self) -> None:
        """Sending None produces a badArgument that the classifier would file as
        'unfetchable', so an entire run would report zero recovered and look like
        a flaky source rather than a broken request."""
        assert self._captured_prefix(_event(["collect:1"])) is not None

    def test_event_can_still_override(self) -> None:
        event = _event(["collect:1"], metadata_prefix="oai_dc")
        assert self._captured_prefix(event) == "oai_dc"


class TestIdCeilingAndEmptiness:
    def test_rejects_an_empty_id_list(self) -> None:
        """The state machine routes an explicitly supplied empty list here, so
        this is what stops it becoming a silent no-op."""
        with pytest.raises(ValueError, match="at least one record id"):
            _event([])


class TestReporting:
    def _response(self) -> OAIPMHIdLoaderResponse:
        runtime, _ = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )
        response, _ = loader.execute_id_loader(_event(["collect:1"]), runtime)
        return response

    def test_s3_key_disambiguates_runs_in_the_same_minute(self) -> None:
        """job_id is only minute-resolution, and splitting a large recovery
        across runs makes collisions likely."""
        response = self._response()
        keys = {
            OAIPMHIdLoadReport.from_id_load(
                response,
                adapter_type="axiell",
                removed=[],
                unfetchable=[],
                report_s3_bucket="bucket",
            ).s3_uri
            for _ in range(2)
        }
        assert len(keys) == 2

    def test_carries_full_removed_and_unfetchable_lists(self) -> None:
        report = OAIPMHIdLoadReport.from_id_load(
            self._response(),
            adapter_type="axiell",
            removed=["collect:gone"],
            unfetchable=["collect:bad"],
        )
        assert report.removed == ["collect:gone"]
        assert report.unfetchable == ["collect:bad"]

    def test_metrics_can_be_suppressed_for_local_runs(self) -> None:
        report = OAIPMHIdLoadReport.from_id_load(
            self._response(),
            adapter_type="axiell",
            removed=[],
            unfetchable=[],
            emit_metrics=False,
        )
        assert report.publish_to_cloudwatch is False
        assert report.publish_to_s3 is False


def _mock_config() -> MagicMock:
    config = MagicMock()
    config.config.adapter_namespace = "axiell"
    config.config.adapter_name = "axiell"
    config.config.oai_metadata_prefix = CONFIGURED_PREFIX
    config.config.report_s3_bucket = None
    config.config.report_s3_prefix = "dev"
    config.config.window_minutes = 15
    config.build_adapter_store.return_value = MagicMock(spec=AdapterStore)
    config.build_oai_client.return_value = MagicMock(spec=OAIClient)
    config.build_window_store.return_value = MagicMock(spec=WindowStore)
    return config


class TestBuildRuntime:
    def test_id_mode_builds_no_window_store(self) -> None:
        """Building one would open, and locally create, a window-status table for
        a mode that writes no window state."""
        config = _mock_config()

        runtime = loader.build_runtime(config, id_mode=True)

        assert runtime.store is None
        assert runtime.window_generator is None
        config.build_window_store.assert_not_called()
        assert runtime.oai_metadata_prefix == CONFIGURED_PREFIX

    def test_window_mode_still_builds_one(self) -> None:
        config = _mock_config()

        runtime = loader.build_runtime(config)

        assert runtime.store is not None
        assert runtime.window_generator is not None

    def test_window_harvester_refuses_an_id_mode_runtime(self) -> None:
        runtime, _ = _runtime(
            lambda *, identifier, metadata_prefix: _record(identifier)
        )
        with pytest.raises(ValueError, match="built for id mode"):
            loader.build_harvester(MagicMock(), runtime)
