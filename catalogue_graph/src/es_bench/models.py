"""Event and result shapes for the write-throughput harness (platform#6714)."""

from typing import Literal

from pydantic import BaseModel, Field


class EsTarget(BaseModel):
    """One cluster to talk to, resolved from Secrets Manager or given literally."""

    name: str
    host_secret: str | None = None
    port_secret: str | None = None
    protocol_secret: str | None = None
    api_key_secret: str | None = None
    host: str | None = None
    port: int = 443
    scheme: str = "https"
    api_key: str | None = None
    http_compress: bool = False
    request_timeout: int = 120
    # elasticsearch-py pools 10 connections per node by default, which caps in-flight bulks.
    connections_per_node: int = 10


class SampleSpec(BaseModel):
    index: str
    count: int = 5000
    slices: int = 4
    page_size: int = 1000
    keep_alive: str = "5m"


class WriteCell(BaseModel):
    bulk_size: int
    concurrency: int


class WriteSpec(BaseModel):
    index: str
    cells: list[WriteCell]
    docs_per_cell: int = 5000
    # Re-runs bump the version so external_gte never reports a conflict.
    version: int | None = None
    id_prefix: str = "bench"
    max_retries: int = 5


class ScrollSpec(BaseModel):
    index: str
    keep_alive: str = "30m"
    page_size: int = 1000
    max_docs: int | None = None


class LoadSpec(BaseModel):
    """Copy one PIT slice of source into dest; the caller fans out over slices."""

    source_index: str
    dest_index: str
    slice_id: int
    slice_max: int
    bulk_size: int = 500
    concurrency: int = 4
    page_size: int = 2000
    keep_alive: str = "10m"
    search_after: list[object] | None = None
    deadline_seconds: int = 780
    # Slices of one copy must share a PIT; separate PITs partition differently.
    pit_id: str | None = None


class CountSpec(BaseModel):
    index: str
    # Distinct values of this keyword field, for coverage checks after a load.
    cardinality_field: str | None = None


class PingSpec(BaseModel):
    requests: int = 50
    # Service keys carry index privileges only, so ping an index rather than the root.
    index: str | None = None


class BenchEvent(BaseModel):
    mode: Literal["write_matrix", "scroll", "load", "ping", "count"]
    source: EsTarget | None = None
    target: EsTarget
    sample: SampleSpec | None = None
    write: WriteSpec | None = None
    scroll: ScrollSpec | None = None
    load: LoadSpec | None = None
    count: CountSpec | None = None
    ping: PingSpec = Field(default_factory=PingSpec)


class LatencyStats(BaseModel):
    count: int
    p50_ms: float
    p95_ms: float
    p99_ms: float
    max_ms: float
    mean_ms: float


class RequestOutcome(BaseModel):
    """What one bulk or search round-trip did, for aggregation."""

    latency_ms: float
    docs: int
    status: int
    retries: int = 0
    item_429s: int = 0
    item_errors: int = 0


class CellResult(BaseModel):
    bulk_size: int
    concurrency: int
    docs: int
    wall_seconds: float
    docs_per_second: float
    requests: int
    latency: LatencyStats
    http_429s: int
    retries: int
    item_429s: int
    item_errors: int
    failed_requests: int
