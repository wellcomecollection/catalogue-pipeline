import html
from typing import Literal
from urllib.parse import urlparse

import nh3
import structlog

logger = structlog.get_logger(__name__)


type AllowedTags = Literal["basic", "none", "italics_only"]

BASIC_TAGS = {
    "a",
    "b",
    "blockquote",
    "br",
    "cite",
    "code",
    "dd",
    "dl",
    "dt",
    "em",
    "i",
    "li",
    "ol",
    "p",
    "pre",
    "q",
    "small",
    "span",
    "strike",
    "strong",
    "sub",
    "sup",
    "u",
    "ul",
}

_TAGS: dict[AllowedTags, set[str]] = {
    "basic": BASIC_TAGS,
    "none": set(),
    "italics_only": {"i"},
}


def normalise_text(s: str, allowed_tags: AllowedTags = "basic") -> str:
    """Sanitise an HTML string, retaining only the tags permitted by allowed_tags."""
    cleaned = nh3.clean(s, tags=_TAGS[allowed_tags], link_rel="nofollow")

    lines = [line.rstrip() for line in cleaned.splitlines()]

    result: list[str] = []
    for line in lines:
        # Strip consecutive blank lines
        if not result and line == "":
            continue
        if result and result[-1] == "" and line == "":
            continue
        result.append(line)

    return html.unescape("\n".join(result)).strip()


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
