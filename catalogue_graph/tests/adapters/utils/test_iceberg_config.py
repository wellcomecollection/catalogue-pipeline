import os
from uuid import uuid4

import pytest
from pyiceberg.partitioning import PartitionSpec
from pyiceberg.schema import Schema

from adapters.utils.iceberg import IcebergTableConfig, get_iceberg_table, get_table


@pytest.fixture()
def local_catalog_params(tmp_path_factory: pytest.TempPathFactory):  # type: ignore
    """Return a dict of parameters (uri, warehouse) for an isolated local catalog.

    Each test gets its own SQLite DB and warehouse directory under the pytest tmp path
    so tables/namespaces don't collide and are automatically cleaned up.
    """
    base = tmp_path_factory.mktemp("iceberg_local")
    db_path = base / "test.db"
    warehouse_dir = base / "warehouse"
    os.makedirs(warehouse_dir, exist_ok=True)
    return {
        "uri": f"sqlite:///{db_path}",
        "warehouse": f"file://{warehouse_dir}/",
    }


def test_get_table_creates_when_flag_true(local_catalog_params):  # type: ignore
    namespace = f"ns_{uuid4().hex[:8]}"
    table_name = f"tbl_{uuid4().hex[:8]}"

    get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=True,
        **local_catalog_params,
    )

    # A second call with create_if_not_exists=True should return the same table (not error)
    same_table = get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=True,
        **local_catalog_params,
    )
    fq_name2 = (
        same_table.name()
        if callable(getattr(same_table, "name", None))
        else getattr(same_table, "identifier", None)
    )
    fq_name2_str = ".".join(fq_name2) if isinstance(fq_name2, tuple) else str(fq_name2)
    assert fq_name2_str == f"{namespace}.{table_name}"


def test_get_table_suppresses_existing_namespace(local_catalog_params):  # type: ignore
    namespace = f"ns_{uuid4().hex[:8]}"
    first_table = f"tbl_{uuid4().hex[:6]}"
    second_table = f"tbl_{uuid4().hex[:6]}"

    # Create first table (also creates namespace)
    get_table(
        catalogue_namespace=namespace,
        table_name=first_table,
        catalogue_name="local",
        create_if_not_exists=True,
        **local_catalog_params,
    )

    # Creating another table in the same existing namespace should not raise
    new_table = get_table(
        catalogue_namespace=namespace,
        table_name=second_table,
        catalogue_name="local",
        create_if_not_exists=True,
        **local_catalog_params,
    )
    fq_new = (
        new_table.name()
        if callable(getattr(new_table, "name", None))
        else getattr(new_table, "identifier", None)
    )
    fq_new_str = ".".join(fq_new) if isinstance(fq_new, tuple) else str(fq_new)
    assert fq_new_str == f"{namespace}.{second_table}"


def test_get_table_loads_when_flag_false(local_catalog_params):  # type: ignore
    namespace = f"ns_{uuid4().hex[:8]}"
    table_name = f"tbl_{uuid4().hex[:8]}"

    created = get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=True,
        **local_catalog_params,
    )

    loaded = get_table(
        catalogue_namespace=namespace,
        table_name=table_name,
        catalogue_name="local",
        create_if_not_exists=False,
        **local_catalog_params,
    )

    # Identifiers match and we can assert the metadata location is identical, indicating load not recreate
    assert created.metadata_location == loaded.metadata_location


def test_get_iceberg_table_local(monkeypatch: pytest.MonkeyPatch) -> None:
    """Test get_iceberg_table with local configuration."""
    namespace = "test_ns"
    table_name = "test_table"
    db_name = "test_db"

    config = IcebergTableConfig(
        table_name=table_name,
        namespace=namespace,
        use_rest_api_table=False,
        db_name=db_name,
    )

    # Mock get_local_table to verify it's called with correct params
    def mock_get_local_table(
        table_name: str,
        namespace: str,
        db_name: str,
        schema: Schema | None = None,
        partition_spec: PartitionSpec | None = None,
        create_if_not_exists: bool = True,
    ) -> str:
        assert table_name == config.table_name
        assert namespace == config.namespace
        assert db_name == config.db_name
        assert create_if_not_exists == config.create_if_not_exists
        return "mock_table"

    monkeypatch.setattr("adapters.utils.iceberg.get_local_table", mock_get_local_table)

    result = get_iceberg_table(config)
    assert result == "mock_table"


def test_get_iceberg_table_rest(monkeypatch: pytest.MonkeyPatch) -> None:
    """Test get_iceberg_table with REST API configuration."""
    namespace = "test_ns"
    table_name = "test_table"
    bucket = "test-bucket"
    region = "us-east-1"
    account_id = "123456789012"

    config = IcebergTableConfig(
        table_name=table_name,
        namespace=namespace,
        use_rest_api_table=True,
        s3_tables_bucket=bucket,
        region=region,
        account_id=account_id,
    )

    # Mock get_rest_api_table to verify it's called with correct params
    def mock_get_rest_api_table(
        s3_tables_bucket: str,
        table_name: str,
        namespace: str,
        create_if_not_exists: bool,
        region: str | None,
        account_id: str | None,
        schema: Schema | None = None,
        partition_spec: PartitionSpec | None = None,
    ) -> str:
        assert s3_tables_bucket == config.s3_tables_bucket
        assert table_name == config.table_name
        assert namespace == config.namespace
        assert create_if_not_exists == config.create_if_not_exists
        assert region == config.region
        assert account_id == config.account_id
        return "mock_rest_table"

    monkeypatch.setattr(
        "adapters.utils.iceberg.get_rest_api_table", mock_get_rest_api_table
    )

    result = get_iceberg_table(config)
    assert result == "mock_rest_table"


def test_get_iceberg_table_rest_missing_bucket() -> None:
    """Test get_iceberg_table raises ValueError when bucket is missing for REST config."""
    config = IcebergTableConfig(
        table_name="test",
        namespace="test",
        use_rest_api_table=True,
        s3_tables_bucket=None,
    )

    with pytest.raises(ValueError, match="s3_tables_bucket must be provided"):
        get_iceberg_table(config)
