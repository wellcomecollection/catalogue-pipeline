import xml.etree.ElementTree as ET
from typing import Any


def assert_get_text(xml_element: Any) -> str:
    """Asserts that the given element is XML contatining text and returns text."""
    assert isinstance(xml_element, ET.Element)

    elem_text = xml_element.text
    assert isinstance(elem_text, str)

    return elem_text
