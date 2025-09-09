import os
import sys
import types
from collections.abc import Generator
from contextlib import suppress
from typing import Any
from uuid import uuid1

import pytest
from _pytest.monkeypatch import MonkeyPatch

# Avoid importing pyiceberg entirely in lightweight unit tests
IcebergTable = Any

# Avoid importing table_config here, it imports boto3/botocore; we'll import lazily in fixtures

# Add the test directory to the path so we can import from it
HERE = os.path.dirname(os.path.realpath(__file__))
PACKAGE_ROOT = os.path.dirname(HERE)
SRC_DIR = os.path.join(PACKAGE_ROOT, "src")
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)

# Pre-stub heavy modules so that imports in test_mocks don't pull real deps
if "botocore" not in sys.modules:
    mod_botocore = types.ModuleType("botocore")
    mod_botocore_credentials = types.ModuleType("botocore.credentials")

    class _Creds:
        def __init__(self, access_key: str, secret_key: str, token: str) -> None:
            self.access_key = access_key
            self.secret_key = secret_key
            self.token = token

    mod_botocore_credentials.Credentials = _Creds  # type: ignore[attr-defined]
    sys.modules["botocore"] = mod_botocore
    sys.modules["botocore.credentials"] = mod_botocore_credentials

if "boto3" not in sys.modules:
    mod_boto3 = types.ModuleType("boto3")
    # Provide placeholders; monkeypatch will override in fixture
    mod_boto3.Session = lambda *a, **k: object()  # type: ignore[attr-defined]
    mod_boto3.resource = lambda *a, **k: object()  # type: ignore[attr-defined]
    sys.modules["boto3"] = mod_boto3

sys.modules.setdefault("requests", types.ModuleType("requests"))
sys.modules.setdefault("smart_open", types.ModuleType("smart_open"))

# Provide a minimal elasticsearch stub with an Elasticsearch attribute so that
# "from elasticsearch import Elasticsearch" succeeds at import time.
_es_mod = sys.modules.setdefault("elasticsearch", types.ModuleType("elasticsearch"))
if not hasattr(_es_mod, "Elasticsearch"):

    class _ES:
        pass

    _es_mod.Elasticsearch = _ES  # type: ignore[attr-defined]

# Ensure the submodule is both present in sys.modules and accessible as an attribute
_helpers_mod = sys.modules.setdefault(
    "elasticsearch.helpers", types.ModuleType("elasticsearch.helpers")
)
# Attach as attribute so getattr(elasticsearch, "helpers") works
_es_mod.helpers = _helpers_mod  # type: ignore[attr-defined]

# Now it's safe to import test_mocks which references botocore Credentials
from .test_mocks import (  # noqa: E402
    MockBoto3Resource,
    MockBoto3Session,
    MockElasticsearchClient,
    MockRequest,
    MockSmartOpen,
)


@pytest.fixture(autouse=True)
def common_mocks(monkeypatch: MonkeyPatch) -> Generator[None, None, None]:
    # Ensure modules exist to avoid importing heavy deps under Python 3.13
    sys.modules.setdefault("boto3", types.ModuleType("boto3"))
    # Minimal botocore stub so tests importing Credentials don't fail under Python 3.13
    if "botocore" not in sys.modules:
        mod_botocore = types.ModuleType("botocore")
        mod_botocore_credentials = types.ModuleType("botocore.credentials")

        class _Creds:
            def __init__(self, access_key: str, secret_key: str, token: str) -> None:
                self.access_key = access_key
                self.secret_key = secret_key
                self.token = token

        mod_botocore_credentials.Credentials = _Creds  # type: ignore[attr-defined]
        sys.modules["botocore"] = mod_botocore
        sys.modules["botocore.credentials"] = mod_botocore_credentials
    else:
        # Modules already provided; nothing to do
        pass
    sys.modules.setdefault("requests", types.ModuleType("requests"))
    smart_open_mod = sys.modules.setdefault(
        "smart_open", types.ModuleType("smart_open")
    )
    # Provide a default open implementation that writes to temp files via MockSmartOpen
    if not hasattr(smart_open_mod, "open"):
        from .test_mocks import MockSmartOpen as _MSO  # local import to avoid cycles
    smart_open_mod.open = _MSO.open  # type: ignore[attr-defined]
    es_mod = sys.modules.setdefault("elasticsearch", types.ModuleType("elasticsearch"))
    helpers_mod = sys.modules.setdefault(
        "elasticsearch.helpers", types.ModuleType("elasticsearch.helpers")
    )
    # Ensure attribute exists so annotated getattr can traverse 'elasticsearch.helpers'
    es_mod.helpers = helpers_mod  # type: ignore[attr-defined]

    monkeypatch.setattr("boto3.Session", MockBoto3Session, raising=False)
    monkeypatch.setattr("boto3.resource", MockBoto3Resource, raising=False)
    monkeypatch.setattr("requests.request", MockRequest.request, raising=False)
    monkeypatch.setattr("requests.get", MockRequest.get, raising=False)
    monkeypatch.setattr("smart_open.open", MockSmartOpen.open, raising=False)
    monkeypatch.setattr(
        "elasticsearch.Elasticsearch", MockElasticsearchClient, raising=False
    )
    monkeypatch.setattr(
        "elasticsearch.helpers.bulk",
        MockElasticsearchClient.bulk,
        raising=False,
    )

    MockRequest.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockElasticsearchClient.reset_mocks()
    yield


@pytest.fixture
def temporary_table() -> Generator[Any, None, None]:
    try:
        from table_config import (
            get_local_table,
        )  # local import to avoid heavy deps at module import time
    except Exception:
        pytest.skip("Iceberg/local table not available; skipping temp table fixture")

    table_name = str(uuid1())
    table = get_local_table(
        table_name=table_name, namespace="test", db_name="test_catalog"
    )
    try:
        yield table
    finally:
        # For cleanup, we need to get the catalog again
        # Since the table object contains the catalog reference, we can use it
        with suppress(Exception):
            table.catalog.drop_table(f"test.{table_name}")


@pytest.fixture
def xml_with_one_record() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_one_record.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_two_records() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_two_records.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_three_records() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_three_records.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def not_xml() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "not_xml.xml")) as xmlfile:
        yield xmlfile
