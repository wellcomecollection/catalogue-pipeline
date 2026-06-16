import pytest

from adapters.transformers.utils.html import format_as_html_link


def test_formats_valid_url_as_link():
    url = "https://example.com/page"
    assert format_as_html_link(url) == '<a href="https://example.com/page">https://example.com/page</a>'


def test_strips_whitespace_before_checking():
    url = "  https://example.com  "
    assert format_as_html_link(url) == '<a href="https://example.com">https://example.com</a>'


def test_returns_non_url_unchanged():
    not_a_url = "just some text"
    assert format_as_html_link(not_a_url) == "just some text"
