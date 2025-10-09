import logging

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.ebsco_to_weco import transform_record


def test_no_description(marc_record: Record) -> None:
    assert transform_record(marc_record).description is None


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
        transform_record(marc_record).description
        == "A statement or account which describes something or someone by listing characteristic features, significant details, etc.; (from OED)"
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
    """
    An <a /> link is created from urls in the $u subfield.

    Aside:
    The previous implementation of this always placed
    the content of subfield $u at the end, regardless of where it
    is in the list of subfields.
    This may or may not be a true requirement
    """
    assert (
        transform_record(marc_record).description
        == 'summary expansion source <a href="http://example.com">http://example.com</a>'
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
def test_only_urls_create_links(
    marc_record: Record, caplog: pytest.LogCaptureFixture
) -> None:
    """
    Any URI that is not a URL is treated as text.
    An <a /> link is not created
    A warning is issued, in case we need to update our url-detection logic.
    """
    with caplog.at_level(logging.WARN):
        assert (
            transform_record(marc_record).description
            == "summary source urn:isbn:9781455841653"
        )
    assert "doesn't look like a URL: urn:isbn:9781455841653" in caplog.text


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
    """
    Any URI that is not a URL is treated as text.
    An <a /> link is not created
    A warning is issued, in case we need to update our url-detection logic.
    """
    assert (
        transform_record(marc_record).description
        == 'summary urn:isbn:9781455841653 <a href="http://example.com">http://example.com</a>'
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="520",
                subfields=[
                    Subfield(code="a", value="hello"),
                ],
            ),
            Field(
                tag="520",
                subfields=[
                    Subfield(code="a", value="world"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_multiple_descriptions(marc_record: Record) -> None:
    """
    multiple descriptions are condensed into one big one, line-separated
    """
    assert transform_record(marc_record).description == "hello\nworld"
