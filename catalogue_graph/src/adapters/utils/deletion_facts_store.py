import pyarrow as pa
import pyarrow.compute as pc
from pyiceberg.expressions import In
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore
from adapters.utils.schemata import DELETION_FACTS_ARROW_SCHEMA


class DeletionFactsStore(PipelineStore):
    """Append-only store of deletion facts (superseded guids).

    Facts are never overwritten: each fact carries a deterministic id
    ("{record_id}/{changeset_id}") so retried writes deduplicate instead of
    duplicating rows. Delivery reads use the inherited
    `get_records_by_changesets`.
    """

    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self) -> pa.Schema:
        return DELETION_FACTS_ARROW_SCHEMA

    @property
    def _compare_columns(self) -> list[str]:
        # Compare columns only drive incremental_update's change detection;
        # this store is append-only and never updates existing rows.
        return []

    def append_facts(self, facts: pa.Table) -> int:
        """Append new facts, skipping any fact ids already in the store.

        Each incoming row must already carry its triggering adapter changeset
        id in `changeset` (set by the caller); ids are not re-tagged here, so
        re-runs preserve the original tagging. Returns the number of rows
        actually appended.
        """
        facts = self.normalise_table(facts)
        if facts.num_rows == 0:
            return 0

        existing = self.get_namespace_records(In("id", self._extract_ids(facts)))
        if existing.num_rows > 0:
            facts = facts.filter(~pc.field("id").isin(existing.column("id")))
        if facts.num_rows == 0:
            return 0

        with self.table.transaction() as tx:
            tx.append(facts)
        return facts.num_rows
