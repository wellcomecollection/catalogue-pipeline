import aiohttp

from .logging import get_logger

logger = get_logger(__name__)

_session_store = {}


def start_persistent_client_session():
    _session_store["session"] = aiohttp.ClientSession()


def close_persistent_client_session():
    try:
        _session_store["session"].close()
        del _session_store["session"]
    except Exception:
        pass


def _get_persistent_session():
    try:
        return _session_store["session"]
    except KeyError as e:
        logger.error("HTTP client session not initialised!")
        raise e


async def fetch_url_bytes(url):
    session = _get_persistent_session()
    async with session.get(url) as response:
        # We need to perform the `read()` here
        # before `response` has been closed
        return {"object": response, "bytes": await response.read()}
