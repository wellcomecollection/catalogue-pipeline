"""Shared Folio (OKAPI) client.

An httpx-based JSON client (:class:`FolioClient`) plus the shared
:class:`OkapiAuth` login flow, which is also used by the FOLIO inventory
enrichment client. See :class:`FolioClient`.
"""

from __future__ import annotations

import os
import ssl

from .client import FolioClient, FolioError
from .inventory_client import FolioInventoryClient
from .okapi_auth import OkapiAuth, OkapiLoginError

__all__ = [
    "FolioClient",
    "FolioError",
    "FolioInventoryClient",
    "OkapiAuth",
    "OkapiLoginError",
    "ssl_context_from_env",
]


def ssl_context_from_env() -> ssl.SSLContext | None:
    """Build an SSL context from FOLIO_CA_BUNDLE / FOLIO_SKIP_TLS_VERIFY.

    Returns ``None`` for the default trust store (the Lambda case). Only a caller
    talking to a Folio behind a custom CA typically needs an override.
    """
    if os.getenv("FOLIO_SKIP_TLS_VERIFY", "false").strip().lower() == "true":
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx
    ca_bundle = os.getenv("FOLIO_CA_BUNDLE", "").strip()
    if ca_bundle:
        return ssl.create_default_context(cafile=ca_bundle)
    return None
