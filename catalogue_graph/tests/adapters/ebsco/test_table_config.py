import os
from uuid import uuid4

import pytest

from adapters.utils.iceberg import get_table


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
