from utils.xml import assert_get_text
import xml.etree.ElementTree as ET
import pytest

def test_assert_get_text():
    elem = ET.Element("root")
    elem.text = "text"

    assert assert_get_text(elem) == "text"

    elem.text = None
    with pytest.raises(AssertionError):
        assert_get_text(elem)

    elem.text = 1
    with pytest.raises(AssertionError):
        assert_get_text(elem)