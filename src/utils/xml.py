from typing import Any
import xml.etree.ElementTree as ET

def get_text(xml_element: Any) -> str:
    """Returns the text content of the given XML element."""
    assert(isinstance(xml_element, ET.Element))
    
    elem_text = xml_element.text
    assert(isinstance(elem_text, str))
    
    return elem_text
