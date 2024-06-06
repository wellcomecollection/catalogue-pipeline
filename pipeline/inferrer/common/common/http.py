import json

import aiohttp

from .logging import get_logger

log = get_logger(__name__)

_session_store = {}


def start_persistent_client_session():
    _session_store["session"] = aiohttp.ClientSession()


async def close_persistent_client_session():
    try:
        await _session_store["session"].close()
        del _session_store["session"]
    except Exception:
        pass


def _get_persistent_session():
    try:
        return _session_store["session"]
    except KeyError as e:
        log.error("HTTP client session not initialised!")
        raise e


async def fetch_url_bytes(url, params=None):
    session = _get_persistent_session()
    async with session.get(url, params=params) as response:
        # We need to perform the `.read()` here before `response` has been
        # closed
        return {"object": response, "bytes": await response.read()}


async def fetch_url_json(url, params=None):
    session = _get_persistent_session()
    async with session.get(url, params=params) as response:
        try:
            # We need to perform the `.json()` here before `response` has been
            # closed
            return {"object": response, "json": await response.json()}
        except (
            json.JSONDecodeError,
            aiohttp.client_exceptions.ContentTypeError,
        ):
            raise ValueError(f"Couldn't decode json from {url}")


async def fetch_redirect_url(url, params=None):
    session = _get_persistent_session()
    async with session.get(url, params=params) as response:
        if response.status == 200:
            return {"object": response, "url": str(response.url)}
        else:
            raise ValueError(f"Looks like {url} isn't a valid url")
