from uuid import uuid1

from pyiceberg.table import Table as IcebergTable

from adapters.extractors.oai_pmh.folio.enrichment.enricher import FolioItemEnricher
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioEnrichedItem,
)
from adapters.steps.oai_pmh.folio_enrich import (
    EnrichmentEvent,
    collect_instance_ids,
    run_enrichment,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from tests.adapters.conftest import adapter_records_to_table

BIB_NAMESPACE = "folio"
ITEMS_NAMESPACE = "folio-items"


class FakeInventoryClient:
    def __init__(self, responses: dict[str, FolioEnrichedInstance]) -> None:
        self.responses = responses

    def enriched_instances(
        self, instance_ids: list[str]
    ) -> list[FolioEnrichedInstance]:
        return [self.responses[i] for i in instance_ids if i in self.responses]


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


def test_run_enrichment_populates_items_store(
    temporary_table: IcebergTable,
) -> None:
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    changeset_id = _seed_bib_changeset(bib_store, ["inst-1", "inst-2"])

    items_store = _local_store(ITEMS_NAMESPACE)
    client = FakeInventoryClient(
        {
            "inst-1": FolioEnrichedInstance(
                instance_id="inst-1", items=[FolioEnrichedItem(id="item-a")]
            ),
            "inst-2": FolioEnrichedInstance(
                instance_id="inst-2", items=[FolioEnrichedItem(id="item-b")]
            ),
        }
    )
    enricher = FolioItemEnricher(client, items_store)  # type: ignore[arg-type]

    response = run_enrichment(
        EnrichmentEvent(job_id="job-1", changeset_ids=[changeset_id]),
        bib_store,
        enricher,
    )

    assert response.changeset_ids == [changeset_id]
    assert len(response.items_changeset_ids) == 1

    stored_ids = {
        r["id"] for r in items_store.get_active_namespace_records().to_pylist()
    }
    assert stored_ids == {"inst-1", "inst-2"}


def test_run_enrichment_with_no_changes_returns_empty_items_changeset(
    temporary_table: IcebergTable,
) -> None:
    bib_store = AdapterStore(temporary_table, BIB_NAMESPACE)
    items_store = _local_store(ITEMS_NAMESPACE)
    enricher = FolioItemEnricher(FakeInventoryClient({}), items_store)  # type: ignore[arg-type]

    response = run_enrichment(
        EnrichmentEvent(job_id="job-1", changeset_ids=[]),
        bib_store,
        enricher,
    )
    assert response.items_changeset_ids == []
