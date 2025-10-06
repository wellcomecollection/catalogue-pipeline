import uuid
from datetime import UTC, datetime
from typing import cast

import pyarrow as pa
import pyarrow.compute as pc
from pyiceberg.expressions import BooleanExpression, EqualTo, In, IsNull, Not
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

from adapters.ebsco import config
from adapters.ebsco.schemata import ARROW_SCHEMA
from adapters.ebsco.table_config import get_local_table, get_rest_api_table


class IcebergTableClient:
    """
    Encapsulates operations on a single Iceberg table.

    Optionally accepts a default_namespace which will be used in update()
    if a namespace isn't provided at call time.
    """

    def __init__(self, table: IcebergTable, default_namespace: str | None = None):
        self.table = table
        self.default_namespace = default_namespace

    def update(
        self, new_data: pa.Table, record_namespace: str | None = None
    ) -> str | None:
        namespace = record_namespace or self.default_namespace
        if namespace is None:
            raise ValueError(
                "record_namespace must be supplied, or default_namespace must be set on the updater"
            )

        # Pull out a table view excluding the existing changeset column, and any deleted rows.
        # the incoming data does not know about changesets, so this allows us to perform a table comparison
        # based on the "real" data.
        existing_data = (
            self.table.scan(
                selected_fields=("namespace", "id", "content"),
            )
            .to_arrow()
            .cast(ARROW_SCHEMA)
        )
        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            deletes = self._get_deletes(existing_data, new_data, namespace)
            updates = self._find_updates(existing_data, new_data)
            inserts = self._find_inserts(existing_data, new_data, namespace)
            changes = pa.concat_tables([deletes, updates])
        else:
            # Empty DB short-circuit, just write it all in.
            inserts = new_data
            changes = None
        if changes or inserts:
            return self._upsert_with_markers(changes, inserts)
        else:
            print("nothing to do")
            return None

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        return self.table.scan(row_filter=EqualTo("changeset", changeset_id)).to_arrow()

    def get_all_records(self, include_deleted: bool = False) -> pa.Table:
        """Return all records in the table.

        By default, rows whose content field is null (i.e. soft-deleted) are excluded.

        During a full reindex we are writing into an empty index,
        so no need to include deleted rows to overwrite documents.

        Set include_deleted=True to return them as well.
        """
        if include_deleted:
            return self.table.scan().to_arrow()
        return self.table.scan(row_filter=Not(IsNull("content"))).to_arrow()

    def _upsert_with_markers(
        self, changes: pa.Table | None, inserts: pa.Table | None
    ) -> str:
        """
        Insert and update records, adding the timestamp and changeset values to
        any changed rows.
        :param changes: New versions of existing records to change
        :param inserts: New records to insert
        """
        changeset_id = str(uuid.uuid1())
        timestamp = pa.scalar(datetime.now(UTC), pa.timestamp("us", "UTC"))
        if changes is not None:
            changes = self._append_change_columns(changes, changeset_id, timestamp)
        if inserts is not None:
            inserts = self._append_change_columns(inserts, changeset_id, timestamp)
        with self.table.transaction() as tx:
            # Because we already know which records to overwrite and which ones to append,
            # we can avoid all the extra processing that happens inside table.upsert to find
            # matching records, check them for differences etc.
            # Just overwrite all the `changes` and append all the `inserts`
            if changes is not None:
                overwrite_mask_predicate = self._create_match_filter(changes)
                tx.overwrite(changes, overwrite_filter=overwrite_mask_predicate)
            if inserts is not None:
                tx.append(inserts)
        return changeset_id

    @staticmethod
    def _create_match_filter(changes: pa.Table) -> BooleanExpression:
        # to_pylist returns list[Any | None]; Iceberg In expects a concrete literal type.
        raw_ids = changes.column("id").to_pylist()
        change_ids = cast(list[str], [i for i in raw_ids if isinstance(i, str)])
        return In("id", change_ids)

    @staticmethod
    def _append_change_columns(
        changeset: pa.Table, changeset_id: str, timestamp: pa.Scalar
    ) -> pa.Table:
        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            [[changeset_id] * len(changeset)],
        ).append_column(
            pa.field("last_modified", type=pa.timestamp("us", "UTC"), nullable=True),
            [[timestamp] * len(changeset)],
        )

        return changeset

    @staticmethod
    def _find_updates(existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
        return get_rows_to_update(new_data, existing_data, ["namespace", "id"])

    @staticmethod
    def _find_inserts(
        existing_data: pa.Table, new_data: pa.Table, record_namespace: str
    ) -> pa.Table:
        old_ids = existing_data.column("id")
        missing_records = new_data.filter(
            (pc.field("namespace") == record_namespace) & ~pc.field("id").isin(old_ids)
        )
        return missing_records

    @staticmethod
    def _get_deletes(
        existing_data: pa.Table, new_data: pa.Table, record_namespace: str
    ) -> pa.Table:
        """
        Find records in `existing_data` that are not in `new_data`, and produce a
        pyarrow Table that can be used to update those records by emptying their content.
        """
        new_ids = new_data.column("id")
        missing_ids = existing_data.filter(
            # records that have already been "deleted" do not need to be deleted again.
            (~pc.field("content").is_null())
            & (pc.field("namespace") == record_namespace)
            & ~pc.field("id").isin(new_ids)
        ).column("id")
        return pa.Table.from_pylist(
            [{"namespace": record_namespace, "id": id.as_py()} for id in missing_ids],
            schema=ARROW_SCHEMA,
        )


# Helper function to get a table based on config
def get_iceberg_table(use_rest_api_table: bool = True) -> IcebergTable:
    if use_rest_api_table:
        print("Using S3 Tables Iceberg REST API table...")
        return get_rest_api_table(
            s3_tables_bucket=config.S3_TABLES_BUCKET,
            table_name=config.REST_API_TABLE_NAME,
            namespace=config.REST_API_NAMESPACE,
            region=config.AWS_REGION,
            account_id=config.AWS_ACCOUNT_ID,
        )
    else:
        print("Using local table...")
        return get_local_table(
            table_name=config.LOCAL_TABLE_NAME,
            namespace=config.LOCAL_NAMESPACE,
            db_name=config.LOCAL_DB_NAME,
        )
