import contextlib
import os
import uuid
from datetime import UTC, datetime
from typing import Any, cast

import boto3
import pyarrow as pa
import pyarrow.compute as pc
from pydantic import BaseModel
from pyiceberg.catalog import load_catalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.expressions import And, BooleanExpression, EqualTo, In, IsNull, Not
from pyiceberg.partitioning import UNPARTITIONED_PARTITION_SPEC, PartitionSpec
from pyiceberg.schema import Schema
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

from adapters.utils.schemata import ARROW_SCHEMA, SCHEMA


class IcebergTableConfig(BaseModel):
    """Configuration for connecting to an Iceberg table."""

    # Common configuration
    table_name: str
    namespace: str
    use_rest_api_table: bool = True
    create_if_not_exists: bool = False

    # REST API configuration (S3 Tables)
    s3_tables_bucket: str | None = None
    region: str | None = None
    account_id: str | None = None

    # Local configuration
    db_name: str | None = None


def get_table(
    catalogue_namespace: str,
    table_name: str,
    catalogue_name: str,
    create_if_not_exists: bool,
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
    **params: Any,
) -> IcebergTable:
    """
    Generic table getter that can be used by any module.

    Args:
        catalogue_name: Name of the catalog
        catalogue_namespace: Namespace for the table
        table_name: Name of the table
        create_if_not_exists: Whether to create the table if it doesn't exist
        schema: Schema to use when creating the table
        partition_spec: Partition spec to use when creating the table
        **params: Additional parameters for loading the catalog

    Returns:
        IcebergTable: The configured table
    """
    catalogue = load_catalog(
        catalogue_name,
        **params,
    )
    table_fullname = f"{catalogue_namespace}.{table_name}"

    if create_if_not_exists:
        with contextlib.suppress(NamespaceAlreadyExistsError):
            catalogue.create_namespace(catalogue_namespace)

        return catalogue.create_table_if_not_exists(
            identifier=table_fullname,
            schema=schema,
            partition_spec=partition_spec or UNPARTITIONED_PARTITION_SPEC,
        )

    return catalogue.load_table(table_fullname)


def get_rest_api_table(
    s3_tables_bucket: str,
    table_name: str,
    namespace: str,
    create_if_not_exists: bool = False,
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
    *,
    region: str | None = None,
    account_id: str | None = None,
) -> IcebergTable:
    """
    Get a table from an S3 Tables Iceberg REST API catalog.

    Args:
        s3_tables_bucket: S3 bucket for the S3 Table
        table_name: Name of the table
        namespace: Namespace for the table
        create_if_not_exists: Whether to create the table if it doesn't exist
        schema: Schema to use when creating the table
        partition_spec: Partition spec to use when creating the table
        region: AWS region where the S3 Table is located
        account_id: AWS account ID

    Returns:
        IcebergTable: The configured table
    """
    session = boto3.Session()
    region = region or session.region_name
    account_id = account_id or session.client("sts").get_caller_identity()["Account"]

    # Use credentials resolved for this session (env vars, shared config, SSO, etc.)
    creds = session.get_credentials()
    if creds is None:
        raise RuntimeError(
            "AWS credentials could not be obtained from the boto3 session."
        )

    frozen = creds.get_frozen_credentials()
    access_key = frozen.access_key
    secret_key = frozen.secret_key
    token = frozen.token
    if not access_key or not secret_key:
        raise RuntimeError(
            "Incomplete AWS credentials: access key or secret key is missing."
        )

    params = {
        "type": "rest",
        "warehouse": f"arn:aws:s3tables:{region}:{account_id}:bucket/{s3_tables_bucket}",
        "uri": f"https://s3tables.{region}.amazonaws.com/iceberg",
        "rest.sigv4-enabled": "true",
        "rest.signing-name": "s3tables",
        "rest.signing-region": region,
        # As we are using the Iceberg REST API exposed by S3 Tables,
        # instead of the Glue catalog, we need S3 credentials here.
        # Using the Glue catalog requires provisioning more complex
        # Lake Formation permissions, which are not required.
        "s3.access-key-id": access_key,
        "s3.secret-access-key": secret_key,
        "s3.region": region,
    }
    if token:
        params["s3.session-token"] = token

    return get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="s3tablescatalog",
        create_if_not_exists=create_if_not_exists,
        schema=schema,
        partition_spec=partition_spec,
        **params,
    )


def get_local_table(
    table_name: str = "mytable",
    namespace: str = "default",
    db_name: str = "catalog",
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    """
    Get a table from the local catalog using the .local directory.

    Args:
        table_name: Name of the table (defaults to "mytable")
        namespace: Namespace for the table (defaults to "default")
        db_name: Database name (defaults to "catalog", use "test_catalog" for tests)
        schema: Schema to use when creating the table
        partition_spec: Partition spec to use when creating the table
        create_if_not_exists: Whether to create the table if it doesn't exist

    Returns:
        IcebergTable: The configured table
    """
    # Get the project root directory (parent of app directory)
    app_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(app_dir)
    local_dir = os.path.join(project_root, ".local")

    # For test databases, use a separate warehouse directory
    if db_name.startswith("test"):
        warehouse_dir = os.path.join(local_dir, "test_warehouse")
    else:
        warehouse_dir = os.path.join(local_dir, "warehouse")

    # Ensure directories exist
    os.makedirs(local_dir, exist_ok=True)
    os.makedirs(warehouse_dir, exist_ok=True)

    return get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=create_if_not_exists,
        schema=schema,
        partition_spec=partition_spec,
        **{
            "uri": f"sqlite:///{os.path.join(local_dir, f'{db_name}.db')}",
            "warehouse": f"file://{warehouse_dir}/",
        },
    )


def get_iceberg_table(
    config: IcebergTableConfig,
    schema: Schema = SCHEMA,
    partition_spec: PartitionSpec | None = None,
) -> IcebergTable:
    """
    Get an Iceberg table based on the provided configuration.

    Args:
        config: Configuration object containing table details.
        schema: Schema to use when creating the table.
        partition_spec: Partition spec to use when creating the table.

    Returns:
        IcebergTable: The configured table.
    """
    if config.use_rest_api_table:
        if not config.s3_tables_bucket:
            raise ValueError(
                "s3_tables_bucket must be provided when use_rest_api_table is True"
            )
        return get_rest_api_table(
            s3_tables_bucket=config.s3_tables_bucket,
            table_name=config.table_name,
            namespace=config.namespace,
            create_if_not_exists=config.create_if_not_exists,
            schema=schema,
            partition_spec=partition_spec,
            region=config.region,
            account_id=config.account_id,
        )

    return get_local_table(
        table_name=config.table_name,
        namespace=config.namespace,
        db_name=config.db_name or "catalog",
        schema=schema,
        partition_spec=partition_spec,
        create_if_not_exists=config.create_if_not_exists,
    )


class IcebergTableClient:
    """
    Encapsulates operations on a single Iceberg table.

    Optionally accepts a default_namespace which will be used in update()
    if a namespace isn't provided at call time.
    """

    def __init__(self, table: IcebergTable, default_namespace: str | None = None):
        self.table = table
        self.default_namespace = default_namespace

    def incremental_update(
        self, new_data: pa.Table, record_namespace: str | None = None
    ) -> str | None:
        """
        Apply an incremental update to the table.
        Only updates and inserts are processed; missing records are NOT deleted.
        """
        namespace = self._get_namespace(record_namespace)
        if new_data.num_rows == 0:
            return None

        # Optimization: For incremental updates, only fetch rows that match the incoming IDs.
        new_ids = [val for val in new_data.column("id").to_pylist() if val is not None]

        # If there are no IDs, there's nothing to update (and inserts would fail if ID is required, but let's assume valid data)
        if not new_ids:
            return None

        row_filter = And(EqualTo("namespace", namespace), In("id", new_ids))
        existing_data = self._get_existing_data(row_filter)

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            updates = self._find_updates(existing_data, new_data)
            inserts = self._find_inserts(existing_data, new_data, namespace)
            changes = updates
        else:
            inserts = new_data
            changes = None

        if changes or inserts:
            return self._upsert_with_markers(changes, inserts)
        return None

    def snapshot_sync(
        self, new_data: pa.Table, record_namespace: str | None = None
    ) -> str | None:
        """
        Sync the table to match the new snapshot.
        Updates, inserts, and DELETES records that are missing from new_data.
        """
        namespace = self._get_namespace(record_namespace)

        # Optimization: Always filter by namespace
        row_filter = EqualTo("namespace", namespace)
        existing_data = self._get_existing_data(row_filter)

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            deletes = self._get_deletes(existing_data, new_data, namespace)
            updates = self._find_updates(existing_data, new_data)
            inserts = self._find_inserts(existing_data, new_data, namespace)

            changes = pa.concat_tables([deletes, updates]) if deletes else updates
        else:
            inserts = new_data
            changes = None

        if changes or inserts:
            return self._upsert_with_markers(changes, inserts)
        return None

    def _get_namespace(self, record_namespace: str | None) -> str:
        namespace = record_namespace or self.default_namespace
        if namespace is None:
            raise ValueError(
                "record_namespace must be supplied, or default_namespace must be set on the updater"
            )
        return namespace

    def _get_existing_data(self, row_filter: BooleanExpression) -> pa.Table:
        return (
            self.table.scan(
                selected_fields=("namespace", "id", "content"),
                row_filter=row_filter,
            )
            .to_arrow()
            .cast(ARROW_SCHEMA)
        )

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
        # Build correctly-typed Arrow arrays for the metadata columns we're appending.
        num_rows = changeset.num_rows
        changeset_array = pa.array([changeset_id] * num_rows, type=pa.string())
        # Convert the Arrow scalar to a Python datetime so the array constructor can repeat it
        last_modified_py = timestamp.as_py()
        last_modified_array = pa.array(
            [last_modified_py] * num_rows,
            type=pa.timestamp("us", "UTC"),
        )

        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            changeset_array,
        ).append_column(
            pa.field("last_modified", type=pa.timestamp("us", "UTC"), nullable=True),
            last_modified_array,
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
