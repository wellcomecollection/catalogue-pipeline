import os
from collections.abc import Iterator
from functools import cache

from lxml import etree

from models.pipeline.id_label import Language

HERE = os.path.dirname(os.path.abspath(__file__))
CODELIST_NS = "info:lc/xmlns/codelist-v1"


def _load_language_xml() -> etree._ElementTree:
    with open(os.path.join(HERE, "languages.xml")) as xml_file:
        return etree.parse(xml_file)


@cache
def _load_languages() -> dict[str, str]:
    doc = _load_language_xml()
    return {code: name for (code, name) in _iter_languages(doc) if code and name}


def _iter_languages(doc: etree._ElementTree) -> Iterator[tuple[str | None, str | None]]:
    ns_decl = {"c": CODELIST_NS}
    code_tag = f"{{{CODELIST_NS}}}code"
    name_tag = f"{{{CODELIST_NS}}}name"
    for language_element in doc.findall("c:languages/c:language", namespaces=ns_decl):
        code = language_element.findtext(code_tag)
        name = language_element.findtext(name_tag)
        yield code, name


def from_code(language_code: str) -> Language | None:
    if language_name := _load_languages().get(language_code):
        return Language(id=language_code, label=language_name)
    return None
