from adapters.transformers.utils.html import format_as_html_link, normalise_text


def test_formats_valid_url_as_link() -> None:
    url = "https://example.com/page"
    assert (
        format_as_html_link(url)
        == '<a href="https://example.com/page">https://example.com/page</a>'
    )


def test_strips_whitespace_before_checking() -> None:
    url = "  https://example.com  "
    assert (
        format_as_html_link(url)
        == '<a href="https://example.com">https://example.com</a>'
    )


def test_returns_non_url_unchanged() -> None:
    not_a_url = "just some text"
    assert format_as_html_link(not_a_url) == "just some text"


def test_normalise_preserves_basic_text_tags() -> None:
    s = "Some text with some <em>basic text tags</em> inside."
    assert normalise_text(s) == s


def test_normalise_strips_non_basic_text_tags() -> None:
    assert (
        normalise_text("<div>Tags should be <ins>stripped</ins></div>")
        == "Tags should be stripped"
    )


def test_normalise_removes_script_tags_and_inner_content() -> None:
    s = 'Nothing here...<script>window.alert("NASTY");</script> Nothing.'
    assert normalise_text(s) == "Nothing here... Nothing."


def test_normalise_does_not_error_on_missing_closing_tags() -> None:
    assert (
        normalise_text("Someone forgot <em>to close something..")
        == "Someone forgot <em>to close something..</em>"
    )


def test_normalise_preserves_newlines_and_left_whitespace() -> None:
    s = "Text\n  * that spans\n  * multiple lines\n(and includes some <b>left whitespace</b>)"
    assert normalise_text(s) == s


def test_normalise_retains_links_stripping_non_href_attributes() -> None:
    s = 'A <a href="http://example.location" target="_blank">link</a>'
    assert (
        normalise_text(s)
        == 'A <a href="http://example.location" rel="nofollow">link</a>'
    )


def test_normalise_does_not_escape_symbols() -> None:
    s = "Ampersand & some other symbols like >."
    assert normalise_text(s) == s


def test_normalise_strips_full_html_document_removing_recurring_empty_lines() -> None:
    s = """
      <!doctype html>
      <html lang="en">
      <head>
        <meta charset="utf-8">
      </head>
      <div>
        <h1>The document:</h1>
        <div>
          <p>
            This contains text with <em>a few</em> <b>different</b> tags, some of
            of which are <span>preserved</span>, others <ins>not</ins>.
          </p>
        </div>
      </div>
    """
    assert (
        normalise_text(s)
        == """The document:

          <p>
            This contains text with <em>a few</em> <b>different</b> tags, some of
            of which are <span>preserved</span>, others not.
          </p>"""
    )
