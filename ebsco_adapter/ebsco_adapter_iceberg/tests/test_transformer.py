import pyarrow as pa
import pytest

from steps.transformer import (
    EbscoAdapterTransformerConfig,
    EbscoAdapterTransformerEvent,
    handler,
)
from utils.iceberg import IcebergTableClient

from .helpers import data_to_namespaced_table
from .test_mocks import MockElasticsearchClient


def test_transformer_end_to_end_with_local_table(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None: 
    xml_records = [
        "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    ]
    pa_table_initial = data_to_namespaced_table(
        [{"id": "batch1", "content": record} for record in xml_records]
    )
    client = IcebergTableClient(temporary_table)
    changeset_id = client.update(pa_table_initial, "ebsco")
    assert changeset_id is not None

    # After creating client and changeset_id, ensure handler uses same table
    monkeypatch.setattr(
        "steps.transformer.get_local_table", lambda **kwargs: temporary_table
    )

    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id, index_date="2025-01-01"
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )

    result = handler(event=event, config_obj=config)

    assert result.records_transformed == 2
    titles = {op["_source"]["title"] for op in MockElasticsearchClient.inputs}
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_no_changeset_returns_zero(monkeypatch: pytest.MonkeyPatch) -> None:
    event = EbscoAdapterTransformerEvent(changeset_id=None)
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )

    result = handler(event=event, config_obj=config)
    assert result.records_transformed == 0
    assert MockElasticsearchClient.inputs == []
