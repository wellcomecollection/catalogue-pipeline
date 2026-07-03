"""Tests covering extraction of MARC 520 into HTML description paragraphs.

https://www.loc.gov/marc/bibliographic/bd520.html

Although the extractor is currently located under the EBSCO adapter module,
these tests are MARC-field level and can be shared across adapters.
"""

from __future__ import annotations

import pytest
from pymarc.record import Field, Record, Subfield
from structlog.testing import capture_logs

from adapters.transformers.marc.description import extract_description


def test_no_description(marc_record: Record) -> None:
    assert extract_description(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="520",
                subfields=[
                    Subfield(
                        code="a",
                        value="A statement or account which describes something or someone",
                    ),
                    Subfield(
                        code="b",
                        value="by listing characteristic features, significant details, etc.;",
                    ),
                    Subfield(code="c", value="(from OED)"),
                    Subfield(code="2", value="HP"),
                    Subfield(code="6", value="obobobo"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_extract_description_from_520(marc_record: Record) -> None:
    assert (
        extract_description(marc_record)
        == "<p>A statement or account which describes something or someone by listing characteristic features, significant details, etc.; (from OED)</p>"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="520",
                subfields=[
                    Subfield(code="a", value="summary"),
                    Subfield(code="u", value="http://example.com"),
                    Subfield(code="b", value="expansion"),
                    Subfield(code="c", value="source"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_make_link_from_url(marc_record: Record) -> None:
    """An <a /> link is created from URLs in the $u subfield."""
    assert (
        extract_description(marc_record)
        == '<p>summary expansion source <a href="http://example.com">http://example.com</a></p>'
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="520",
                subfields=[
                    Subfield(code="a", value="summary"),
                    Subfield(code="u", value="urn:isbn:9781455841653"),
                    Subfield(code="c", value="source"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_only_urls_create_links(marc_record: Record) -> None:
    """Non-URL URIs are treated as text, and a warning is logged."""
    with capture_logs() as cap_logs:
        assert (
            extract_description(marc_record)
            == "<p>summary source urn:isbn:9781455841653</p>"
        )

    assert any(
        "$u subfield doesn't look like a URL" in log.get("event", "")
        for log in cap_logs
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="520",
                subfields=[
                    Subfield(code="a", value="summary"),
                    Subfield(code="u", value="urn:isbn:9781455841653"),
                    Subfield(code="u", value="http://example.com"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_multiple_urls(marc_record: Record) -> None:
    assert (
        extract_description(marc_record)
        == '<p>summary urn:isbn:9781455841653 <a href="http://example.com">http://example.com</a></p>'
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(tag="520", subfields=[Subfield(code="a", value="hello")]),
            Field(tag="520", subfields=[Subfield(code="a", value="world")]),
        )
    ],
    indirect=True,
)
def test_multiple_descriptions(marc_record: Record) -> None:
    """Multiple 520 fields are condensed into one, line-separated."""
    assert extract_description(marc_record) == "<p>hello</p>\n<p>world</p>"
