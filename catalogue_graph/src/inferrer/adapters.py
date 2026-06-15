"""HTTP adapters for the three inferrer sidecars.

Ports `inference_manager/.../adapters/*.scala`. Each adapter knows the sidecar's
HTTP path and how to turn its JSON response into a partial `InferredData` dict.
The inferrers encode float vectors as base64 little-endian float32, matching the
Scala `AdapterCommon.decodeBase64ToFloatList`.
"""

from __future__ import annotations

import base64
import os
import struct
from collections.abc import Callable
from dataclasses import dataclass

import requests

# A VGG-19 feature vector has exactly 4096 dimensions; anything else is treated
# as an absent feature vector (parity with `FeatureVectorInferrerAdapter`).
FEATURE_VECTOR_SIZE = 4096

DEFAULT_PORTS = {
    "feature": 3141,
    "palette": 3142,
    "aspect_ratio": 3143,
}


class InferrerError(Exception):
    pass


def decode_base64_floats(encoded: str) -> list[float]:
    """Decode a base64 little-endian float32 buffer into a list of floats."""
    raw = base64.b64decode(encoded)
    count = len(raw) // 4
    return list(struct.unpack(f"<{count}f", raw[: count * 4]))


def parse_features(response: dict) -> dict:
    # Return whatever was decoded; the manager validates the length (a vector
    # that isn't exactly FEATURE_VECTOR_SIZE long is treated as a poisoned doc
    # and fails the task rather than being silently indexed).
    return {"features": decode_base64_floats(response["features_b64"])}


def parse_palette(response: dict) -> dict:
    return {
        "palette_embedding": decode_base64_floats(response["palette_embedding"]),
        "average_color_hex": response["average_color_hex"],
    }


def parse_aspect_ratio(response: dict) -> dict:
    # Keep `None` if absent so the manager can detect (and reject) a poisoned doc.
    return {"aspect_ratio": response.get("aspect_ratio")}


@dataclass(frozen=True)
class Inferrer:
    name: str
    path: str
    parse: Callable[[dict], dict]

    @property
    def base_url(self) -> str:
        prefix = self.name.upper()
        host = os.environ.get(f"{prefix}_INFERRER_HOST", "localhost")
        port = os.environ.get(f"{prefix}_INFERRER_PORT", str(DEFAULT_PORTS[self.name]))
        return f"http://{host}:{port}"


INFERRERS: list[Inferrer] = [
    Inferrer("feature", "/feature-vector/", parse_features),
    Inferrer("palette", "/palette/", parse_palette),
    Inferrer("aspect_ratio", "/aspect-ratio/", parse_aspect_ratio),
]


def call_inferrer(inferrer: Inferrer, file_url: str, timeout: float) -> dict:
    """Call a single inferrer and return its partial `InferredData` contribution.

    Raises `InferrerError` on any non-200 response (parity with the Scala
    `parseResponse`), so the caller can drop an image that did not get a
    response from every inferrer.
    """
    response = requests.get(
        f"{inferrer.base_url}{inferrer.path}",
        params={"query_url": file_url},
        timeout=timeout,
    )
    if response.status_code != 200:
        raise InferrerError(
            f"{inferrer.name} inferrer request failed with status "
            f"{response.status_code}"
        )
    return inferrer.parse(response.json())
