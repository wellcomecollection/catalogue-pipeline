import base64
import itertools
from collections.abc import Iterable

import pytest
import requests
from _pytest.monkeypatch import MonkeyPatch

from inferrer import adapters
from inferrer.adapters import (
    FEATURE_VECTOR_SIZE,
    INFERRERS,
    Inferrer,
    InferrerError,
    call_inferrer,
    decode_base64_floats,
    parse_aspect_ratio,
    parse_features,
    parse_palette,
)
from tests.inferrer.factories import encode_floats
from tests.mocks import MockRequest, MockResponse


def _inferrer(name: str) -> Inferrer:
    return next(i for i in INFERRERS if i.name == name)


def test_decode_base64_floats_is_little_endian() -> None:
    floats = [1.0, -2.5, 3.25, 0.0]
    assert decode_base64_floats(encode_floats(floats)) == pytest.approx(floats)


def test_decode_base64_floats_rejects_non_multiple_of_4() -> None:
    # 5 raw bytes is not a whole number of float32 values; decoding should fail
    # loudly rather than silently truncating to one float.
    encoded = base64.b64encode(b"\x00\x00\x00\x00\x00").decode()
    with pytest.raises(InferrerError):
        decode_base64_floats(encoded)


def test_parse_features_decodes_vector() -> None:
    floats = [0.5] * FEATURE_VECTOR_SIZE
    parsed = parse_features({"features_b64": encode_floats(floats)})
    assert parsed["features"] == pytest.approx(floats)


def test_parse_palette_decodes_embedding_and_hex() -> None:
    parsed = parse_palette(
        {"palette_embedding": encode_floats([0.1, 0.2]), "average_color_hex": "#abcdef"}
    )
    assert parsed["palette_embedding"] == pytest.approx([0.1, 0.2])
    assert parsed["average_color_hex"] == "#abcdef"


def test_parse_aspect_ratio_present_and_absent() -> None:
    assert parse_aspect_ratio({"aspect_ratio": 1.5})["aspect_ratio"] == 1.5
    # Missing aspect ratio is surfaced as None so the manager can reject it.
    assert parse_aspect_ratio({})["aspect_ratio"] is None


def test_call_inferrer_success() -> None:
    inferrer = _inferrer("aspect_ratio")
    MockRequest.mock_response(
        method="GET",
        url=f"{inferrer.base_url}{inferrer.path}",
        params={"query_url": "file:///data/x/default.jpg"},
        json_data={"aspect_ratio": 2.0},
    )
    result = call_inferrer(inferrer, "file:///data/x/default.jpg", timeout=5)
    assert result["aspect_ratio"] == 2.0


def test_call_inferrer_non_200_raises() -> None:
    inferrer = _inferrer("feature")
    MockRequest.mock_response(
        method="GET",
        url=f"{inferrer.base_url}{inferrer.path}",
        params={"query_url": "file:///data/x/default.jpg"},
        status_code=404,
        json_data={},
    )
    with pytest.raises(InferrerError):
        call_inferrer(inferrer, "file:///data/x/default.jpg", timeout=5)


def _patch_get_sequence(
    monkeypatch: MonkeyPatch, items: Iterable, no_sleep: bool = True
) -> dict:
    """Patch requests.get to return/raise each item in turn; counts calls."""
    seq = iter(items)
    state = {"calls": 0}

    def fake_get(url: str, **kwargs: object) -> object:
        state["calls"] += 1
        item = next(seq)
        if isinstance(item, Exception):
            raise item
        return item

    monkeypatch.setattr(adapters.requests, "get", fake_get)
    if no_sleep:
        monkeypatch.setattr("time.sleep", lambda *_a, **_k: None)
    return state


def test_call_inferrer_retries_dropped_connection(monkeypatch: MonkeyPatch) -> None:
    state = _patch_get_sequence(
        monkeypatch,
        [
            requests.exceptions.ConnectionError("Remote end closed connection"),
            MockResponse(200, json_data={"aspect_ratio": 2.0}),
        ],
    )
    result = call_inferrer(_inferrer("aspect_ratio"), "file:///data/x/default.jpg", 5)
    assert result["aspect_ratio"] == 2.0
    assert state["calls"] == 2


def test_call_inferrer_gives_up_on_persistent_connection_errors(
    monkeypatch: MonkeyPatch,
) -> None:
    # Real sleeps here: backoff caps each wait at the remaining 0.05s budget.
    monkeypatch.setattr(adapters, "CONNECTION_RETRY_SECONDS", 0.05)
    state = _patch_get_sequence(
        monkeypatch,
        itertools.repeat(requests.exceptions.ConnectionError("refused")),
        no_sleep=False,
    )
    with pytest.raises(requests.exceptions.ConnectionError):
        call_inferrer(_inferrer("feature"), "file:///data/x/default.jpg", 5)
    assert state["calls"] > 1
