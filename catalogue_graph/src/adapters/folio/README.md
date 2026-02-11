# FOLIO OAI-PMH Adapter

This adapter ingests records from the FOLIO OAI-PMH feed. It extends the shared [OAI-PMH adapter framework](../oai_pmh/README.md).

## Configuration

| Setting | Value | Notes |
| --- | --- | --- |
| Metadata prefix | `marc21_withholdings` | MARC21 with holdings information |
| Set spec | None | Harvests all records (no filtering) |
| Auth header | `Authorization` | Standard HTTP auth header |
| Default window | 15 minutes | `FOLIO_WINDOW_MINUTES` |
| Lookback | 7 days | `FOLIO_WINDOW_LOOKBACK_DAYS` |

## SSM Parameters

| Parameter | Description |
| --- | --- |
| `/catalogue_pipeline/folio/oai_api_token` | FOLIO OAI API token |
| `/catalogue_pipeline/folio/oai_api_url` | FOLIO OAI-PMH endpoint URL |

## Quick start

See the [OAI-PMH README](../oai_pmh/README.md) for detailed step-by-step instructions. Replace `{adapter}` with `folio`.

```bash
# Trigger
uv run python -m adapters.folio.steps.trigger --at 2025-11-17T12:15:00Z > /tmp/folio_event.json

# Loader
uv run python -m adapters.folio.steps.loader --event /tmp/folio_event.json

# Reloader (fill gaps)
uv run python -m adapters.folio.steps.reloader \
  --job-id gap-reload \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

## Environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `FOLIO_WINDOW_MINUTES` | 15 | Harvesting window duration |
| `FOLIO_WINDOW_LOOKBACK_DAYS` | 7 | Fallback range when no history |
| `FOLIO_MAX_LAG_MINUTES` | 360 | Maximum allowed lag before circuit breaker |
| `FOLIO_OAI_SET_SPEC` | None | OAI set specification (empty = all records) |
| `FOLIO_OAI_METADATA_PREFIX` | `marc21_withholdings` | OAI metadata prefix |
