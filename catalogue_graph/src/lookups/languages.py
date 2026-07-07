import os
from collections import defaultdict
from collections.abc import Iterator
from functools import cache

import structlog
from lxml import etree

from models.pipeline.id_label import Language

logger = structlog.get_logger(__name__)


HERE = os.path.dirname(os.path.abspath(__file__))
CODELIST_NS = "info:lc/xmlns/codelist-v1"


def _load_language_xml() -> etree._ElementTree:
    with open(os.path.join(HERE, "languages.xml")) as xml_file:
        return etree.parse(xml_file)


@cache
def load_language_code_to_name_map() -> dict[str, str]:
    doc = _load_language_xml()
    return {code: name for (code, name) in _iter_languages(doc) if code and name}


@cache
def load_language_name_to_codes_map() -> dict[str, list[str]]:
    doc = _load_language_xml()
    mapping: defaultdict[str, list[str]] = defaultdict(list)

    # For languages with multiple codes (e.g. "hrv" and "scr" for Croatian),
    # we do not want to include codes marked as obsolete in the mapping
    for code, name in _iter_languages(
        doc, include_name_variants=True, skip_obsolete_codes=True
    ):
        if code and name:
            mapping[name].append(code)

    return dict(mapping)


def _iter_languages(
    doc: etree._ElementTree,
    include_name_variants: bool = False,
    skip_obsolete_codes: bool = False,
) -> Iterator[tuple[str | None, str | None]]:
    ns_decl = {"c": CODELIST_NS}
    code_tag = f"{{{CODELIST_NS}}}code"
    name_tag = f"{{{CODELIST_NS}}}name"

    for language_element in doc.findall("c:languages/c:language", namespaces=ns_decl):
        code_element = language_element.find(code_tag)
        if code_element is None:
            continue
        if skip_obsolete_codes and code_element.get("status") == "obsolete":
            continue

        name = language_element.findtext(name_tag)
        code = code_element.text

        if include_name_variants:
            # Find all descendant instances of `<name>` to make sure we yield all variant names
            for name_element in language_element.findall(f".//{name_tag}"):
                yield code, name_element.text
        else:
            yield code, name


def from_code(language_code: str) -> Language | None:
    if language_name := load_language_code_to_name_map().get(language_code):
        return Language(id=language_code, label=language_name)
    return None


def from_name(language_name: str) -> Language | None:
    language_codes = load_language_name_to_codes_map().get(language_name)

    if not language_codes:
        return None

    if len(language_codes) > 1:
        logger.warning(
            "Multiple language codes for language name",
            name=language_name,
            codes=language_codes,
        )

    return Language(label=language_name, id=language_codes[0])
