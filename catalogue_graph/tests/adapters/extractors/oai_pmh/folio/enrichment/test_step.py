from typing import Any
from uuid import uuid1

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.extractors.oai_pmh.folio.enrichment.enricher import (
    REJECTED_FLOOR,
    FolioInventoryEnricher,
    SystemicRejectionError,
)
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    InventoryFetch,
    RejectedInventoryRecord,
    parse_inventory_records,
)
from adapters.extractors.oai_pmh.folio.enrichment.reporting import FolioEnrichmentReport
from adapters.steps.oai_pmh.folio_enrich import (
    EnrichmentEvent,
    collect_instance_ids,
    run_enrichment,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from tests.adapters.conftest import adapter_records_to_table
from tests.adapters.transformers.folio.helpers import fixture_record

BIB_NAMESPACE = "folio"
INVENTORY_NAMESPACE = "folio-inventory"


class FakeInventoryClient:
    def __init__(self, responses: dict[str, dict[str, Any]]) -> None:
        self.responses = responses

    def items_and_holdings(self, instance_ids: list[str]) -> InventoryFetch:
        return parse_inventory_records(
            [self.responses[i] for i in instance_ids if i in self.responses]
        )


def _local_store(namespace: str) -> AdapterStore:
    config = LocalIcebergTableConfig(
        table_name=str(uuid1()), namespace="test", db_name="test_catalog"
    )
    return AdapterStore(get_local_table(config), namespace)


def _seed_bib_changeset(store: AdapterStore, ids: list[str]) -> str:
    rows = adapter_records_to_table(
        [{"id": i, "content": f"<record>{i}</record>"} for i in ids],
        namespace=store.namespace,
    )
    update = store.incremental_update(rows)
    assert update is not None
    return update.changeset_id


def test_collect_instance_ids_dedupes_across_changesets(
    temporary_table: IcebergTable,
) -> None:
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    cs1 = _seed_bib_changeset(bib_store, ["inst-1", "inst-2"])
    cs2 = _seed_bib_changeset(bib_store, ["inst-3"])

    ids = collect_instance_ids(bib_store, [cs1, cs2])
    assert sorted(ids) == ["inst-1", "inst-2", "inst-3"]


def test_run_enrichment_populates_inventory_store(
    temporary_table: IcebergTable,
) -> None:
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    changeset_id = _seed_bib_changeset(bib_store, ["inst-1", "inst-2"])

    inventory_store = _local_store(INVENTORY_NAMESPACE)
    client = FakeInventoryClient(
        {
            "inst-1": fixture_record("inst-1"),
            "inst-2": fixture_record("inst-2"),
        }
    )
    enricher = FolioInventoryEnricher(client, inventory_store)  # type: ignore[arg-type]

    response = run_enrichment(
        EnrichmentEvent(job_id="job-1", changeset_ids=[changeset_id]),
        bib_store,
        enricher,
    )

    assert response.changeset_ids == [changeset_id]
    assert len(response.items_changeset_ids) == 1
    assert (response.requested_count, response.rejected_count) == (2, 0)

    stored_ids = {
        r["id"] for r in inventory_store.get_active_namespace_records().to_pylist()
    }
    assert stored_ids == {"inst-1", "inst-2"}


def test_run_enrichment_with_no_changes_returns_empty_items_changeset(
    temporary_table: IcebergTable,
) -> None:
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    inventory_store = _local_store(INVENTORY_NAMESPACE)
    enricher = FolioInventoryEnricher(FakeInventoryClient({}), inventory_store)  # type: ignore[arg-type]

    response = run_enrichment(
        EnrichmentEvent(job_id="job-1", changeset_ids=[]),
        bib_store,
        enricher,
    )
    assert response.items_changeset_ids == []


def test_run_enrichment_reports_a_rejection_without_failing(
    temporary_table: IcebergTable,
) -> None:
    """A single bad record is reported with failed=False, which is what the alarm and
    the S3 report rely on, and the rest of the window is stored."""
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    changeset_id = _seed_bib_changeset(bib_store, ["inst-1", "inst-2", "inst-3"])
    broken = fixture_record("inst-2")
    del broken["holdings"][0]["id"]
    client = FakeInventoryClient(
        {
            "inst-1": fixture_record("inst-1"),
            "inst-2": broken,
            "inst-3": fixture_record("inst-3"),
        }
    )
    inventory_store = _local_store(INVENTORY_NAMESPACE)
    enricher = FolioInventoryEnricher(client, inventory_store)  # type: ignore[arg-type]
    reports: list[tuple[int, list[str | None], bool]] = []

    response = run_enrichment(
        EnrichmentEvent(job_id="job-1", changeset_ids=[changeset_id]),
        bib_store,
        enricher,
        lambda requested, rejected, failed: reports.append(
            (requested, [r.instance_id for r in rejected], failed)
        ),
    )

    assert reports == [(3, ["inst-2"], False)]
    assert (response.requested_count, response.rejected_count) == (3, 1)
    stored = {
        r["id"] for r in inventory_store.get_active_namespace_records().to_pylist()
    }
    assert stored == {"inst-1", "inst-3"}


def test_run_enrichment_reports_rejections_before_failing(
    temporary_table: IcebergTable,
) -> None:
    """The rejected list is reported even when the step fails on systemic rejection."""
    ids = [f"inst-{n}" for n in range(REJECTED_FLOOR + 1)]
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    changeset_id = _seed_bib_changeset(bib_store, ids)
    broken = {}
    for i in ids:
        record = fixture_record(i)
        del record["holdings"][0]["id"]
        broken[i] = record
    client = FakeInventoryClient(broken)
    enricher = FolioInventoryEnricher(client, _local_store(INVENTORY_NAMESPACE))  # type: ignore[arg-type]
    reports: list[tuple[int, int, bool]] = []

    with pytest.raises(SystemicRejectionError):
        run_enrichment(
            EnrichmentEvent(job_id="job-1", changeset_ids=[changeset_id]),
            bib_store,
            enricher,
            lambda requested, rejected, failed: reports.append(
                (requested, len(rejected), failed)
            ),
        )

    assert reports == [(len(ids), len(ids), True)]


def test_report_writes_the_rejected_list_only_when_there_is_one() -> None:
    rejected = [RejectedInventoryRecord(instance_id="i-1", reason="missing an id")]
    report = FolioEnrichmentReport.build(
        adapter_type="folio",
        job_id="20261020T1200",
        requested_count=5,
        rejected=rejected,
        report_s3_bucket="bucket",
        report_s3_prefix="prod",
    )
    assert report.publish_to_s3
    assert report.s3_uri.startswith(
        "s3://bucket/prod/reports/folio/adapter_enrichment/20261020T1200_"
    )
    assert {m.name: m.value for m in report.metrics} == {
        "requested_count": 5,
        "rejected_record_count": 1,
    }
    assert report.model_dump(mode="json")["rejected"] == [
        {"instance_id": "i-1", "reason": "missing an id"}
    ]

    clean = FolioEnrichmentReport.build(
        adapter_type="folio",
        job_id="20261020T1200",
        requested_count=5,
        rejected=[],
        report_s3_bucket="bucket",
    )
    assert not clean.publish_to_s3
