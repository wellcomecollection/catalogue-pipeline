from datetime import datetime

import pytest
from pymarc.record import Field, Subfield
from structlog.testing import capture_logs

from adapters.extractors.oai_pmh.axiell import config as axiell_config
from adapters.extractors.oai_pmh.axiell.config import _parse_collection_path_source
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from tests.adapters.transformers.axiell.conftest import make_axiell_record

# mypy: allow-untyped-calls


@pytest.mark.parametrize("value", ["refno", "part_of"])
def test_known_collection_path_source_is_accepted(value: str) -> None:
    assert _parse_collection_path_source(value) == value


@pytest.mark.parametrize("value", ["", "partof", "RefNo", "part-of"])
def test_unknown_collection_path_source_fails(value: str) -> None:
    with pytest.raises(ValueError, match="AXIELL_COLLECTION_PATH_SOURCE"):
        _parse_collection_path_source(value)


def test_collection_path_source_defaults_to_refno() -> None:
    assert axiell_config.AXIELL_COLLECTION_PATH_SOURCE == "refno"


def test_multiple_982_warning_is_logged_once_per_record(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(axiell_config, "AXIELL_COLLECTION_PATH_SOURCE", "part_of")
    record = make_axiell_record(part_of="PP/MIA")
    record.add_field(*make_axiell_record(part_of="PP/OTHER").get_fields("982"))
    record.add_field(
        Field(tag="035", subfields=[Subfield(code="a", value="(AltRefNo)PP/MIA/1")])
    )
    with capture_logs() as logs:
        AxiellWorkBuilder(record, last_modified=datetime(2020, 1, 1)).transform_work()
    warnings = [entry for entry in logs if "982" in entry["event"]]
    assert len(warnings) == 1
