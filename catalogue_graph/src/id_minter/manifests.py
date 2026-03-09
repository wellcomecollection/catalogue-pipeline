"""ID minter manifest writer.

Subclasses the shared ManifestWriter, emitting batch lines with
``canonicalIds`` instead of source identifiers.
"""

from __future__ import annotations

from utils.manifests import BatchLine, ManifestWriter


class CanonicalIdBatchLine(BatchLine):
    """Batch line for the id minter — carries minted canonical IDs."""

    canonicalIds: list[str]


class IdMinterManifestWriter(ManifestWriter):
    """ManifestWriter that writes canonical IDs to the success batch file."""

    def _make_batch_line(self, ids: list[str]) -> CanonicalIdBatchLine:
        return CanonicalIdBatchLine(canonicalIds=ids, jobId=self.job_id)
