from urllib.parse import urlparse


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
