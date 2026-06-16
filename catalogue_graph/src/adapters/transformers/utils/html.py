from urllib.parse import urlparse

import structlog

logger = structlog.get_logger(__name__)


def is_url(maybe_url: str) -> bool:
    """
    A potential URL is only considered a URL for linking purposes if it
    has a webpage-appropriate scheme and would actually go somewhere.

    This test is actually slightly stricter than the old Scala way, which
    would allow jar:// and ftp:// urls, but in the data seen so far
    from EBSCO, all $u subfields are http urls.
    """
    url = urlparse(maybe_url)
    # urlparse is very lenient in what it accepts and parses.
    # e.g. If it's not a fully qualified URL, it is interpreted as relative.
    #
    # So we need to do a bit of extra checking to see if it really
    # looks like a URL.
    return bool(url.scheme in ["http", "https"] and url.netloc)


def format_as_html_link(maybe_url: str) -> str:
    """Format $u subfield contents as an HTML link if valid URL, otherwise return contents unchanged."""
    trimmed = maybe_url.strip()
    if is_url(trimmed):
        return f'<a href="{trimmed}">{trimmed}</a>'

    logger.warning("$u subfield doesn't look like a URL", value=maybe_url)
    return maybe_url
