import xml.etree.ElementTree as ET

import pytest

from utils.xml import assert_get_text


def test_assert_get_text() -> None:
    elem = ET.Element("root")
    elem.text = "text"

    assert assert_get_text(elem) == "text"

    elem.text = None
    with pytest.raises(AssertionError):
        assert_get_text(elem)

    elem.text = 1  # type: ignore  # Deliberate type error to test assertion
    with pytest.raises(AssertionError):
        assert_get_text(elem)
