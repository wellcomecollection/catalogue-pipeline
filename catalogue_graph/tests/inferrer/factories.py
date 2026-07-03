"""Test factories for image inference."""

from __future__ import annotations

import base64
import struct

from inferrer.models import InitialImage


def encode_floats(floats: list[float]) -> str:
    """Encode floats as the inferrers do: base64 little-endian float32."""
    return base64.b64encode(struct.pack(f"<{len(floats)}f", *floats)).decode()


def iiif_location(url: str) -> dict:
    return {
        "type": "DigitalLocation",
        "locationType": {"id": "iiif-image"},
        "accessConditions": [],
        "url": url,
    }


def initial_image_doc(canonical_id: str, location_url: str) -> dict:
    """A minimal-but-valid `images-initial` document (camelCase, as in ES)."""
    return {
        "version": 1,
        "modifiedTime": "2026-06-10T12:00:00Z",
        "state": {
            "canonicalId": canonical_id,
            "sourceIdentifier": {
                "identifierType": {"id": "miro-image-number"},
                "ontologyType": "Image",
                "value": f"V-{canonical_id}",
            },
        },
        "source": {
            "id": {
                "sourceIdentifier": {
                    "identifierType": {"id": "sierra-system-number"},
                    "ontologyType": "Work",
                    "value": "b1234567",
                },
                "canonicalId": "work1234",
            },
            "data": {},
            "version": 1,
        },
        "locations": [iiif_location(location_url)],
    }


def make_initial_image(canonical_id: str, location_url: str) -> InitialImage:
    return InitialImage.model_validate(initial_image_doc(canonical_id, location_url))
