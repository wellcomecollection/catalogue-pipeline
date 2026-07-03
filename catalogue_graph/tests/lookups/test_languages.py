from lookups.languages import (
    _iter_languages,
    _load_language_xml,
    load_language_name_to_codes_map,
)

# --- obsolete code handling --------------------------------------------------


def test_obsolete_codes_included_by_default() -> None:
    # "ajm" (Aljamía) is marked status="obsolete" in languages.xml
    doc = _load_language_xml()
    results = list(_iter_languages(doc))
    assert ("ajm", "Aljamía") in results


def test_obsolete_codes_skipped_when_flag_set() -> None:
    doc = _load_language_xml()
    results = list(_iter_languages(doc, skip_obsolete_codes=True))
    codes = [code for code, _ in results]
    assert "ajm" not in codes
    assert "scr" not in codes


def test_non_obsolete_codes_not_skipped() -> None:
    doc = _load_language_xml()
    results = list(_iter_languages(doc, skip_obsolete_codes=True))
    assert ("eng", "English") in results


# --- name variant handling ---------------------------------------------------


def test_name_variants_excluded_by_default() -> None:
    # "ace" (Achinese) has variant name "Atjeh" in languages.xml
    doc = _load_language_xml()
    results = list(_iter_languages(doc))
    assert ("ace", "Achinese") in results
    assert ("ace", "Atjeh") not in results


def test_name_variants_included_when_flag_set() -> None:
    doc = _load_language_xml()
    results = list(_iter_languages(doc, include_name_variants=True))
    assert ("ace", "Achinese") in results
    assert ("ace", "Atjeh") in results


def test_multiple_name_variants_all_included() -> None:
    # "ach" (Acoli) has variants "Acholi", "Gang", "Lwo", "Shuli" in languages.xml
    doc = _load_language_xml()
    results = list(_iter_languages(doc, include_name_variants=True))
    assert ("ach", "Acholi") in results
    assert ("ach", "Gang") in results
    assert ("ach", "Lwo") in results


# --- load_language_name_to_codes_map -----------------------------------------


def test_obsolete_codes_absent_from_name_to_codes_map() -> None:
    mapping = load_language_name_to_codes_map()
    assert "ajm" not in [code for codes in mapping.values() for code in codes]


def test_name_variants_present_in_name_to_codes_map() -> None:
    mapping = load_language_name_to_codes_map()
    assert "Atjeh" in mapping
    assert "ace" in mapping["Atjeh"]
