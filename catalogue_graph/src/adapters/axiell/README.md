# Axiell OAI-PMH Adapter

This adapter ingests records from the Axiell Collections OAI-PMH feed. It extends the shared [OAI-PMH adapter framework](../oai_pmh/README.md).

## Configuration

| Setting | Value | Notes |
| --- | --- | --- |
| Metadata prefix | `oai_marcxml` | Axiell-specific MARC XML format |
| Set spec | `collect` | Filters to collection records only |
| Auth header | `Token` | Custom header (not standard Authorization) |
| Default window | 15 minutes | `AXIELL_WINDOW_MINUTES` |
| Lookback | 7 days | `AXIELL_WINDOW_LOOKBACK_DAYS` |

## SSM Parameters

| Parameter | Description |
| --- | --- |
| `/catalogue_pipeline/axiell/oai_api_token` | Axiell OAI API token |
| `/catalogue_pipeline/axiell/oai_api_url` | Axiell OAI-PMH endpoint URL |

## Quick start

See the [OAI-PMH README](../oai_pmh/README.md) for detailed step-by-step instructions. Replace `{adapter}` with `axiell`.

```bash
# Trigger
uv run python -m adapters.axiell.steps.trigger --at 2025-11-17T12:15:00Z > /tmp/axiell_event.json

# Loader
uv run python -m adapters.axiell.steps.loader --event /tmp/axiell_event.json

# Reloader (fill gaps)
uv run python -m adapters.axiell.steps.reloader \
  --job-id gap-reload \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

## Environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `AXIELL_WINDOW_MINUTES` | 15 | Harvesting window duration |
| `AXIELL_WINDOW_LOOKBACK_DAYS` | 7 | Fallback range when no history |
| `AXIELL_MAX_LAG_MINUTES` | 360 | Maximum allowed lag before circuit breaker |
| `AXIELL_OAI_SET_SPEC` | `collect` | OAI set specification |
| `AXIELL_OAI_METADATA_PREFIX` | `oai_marcxml` | OAI metadata prefix |

