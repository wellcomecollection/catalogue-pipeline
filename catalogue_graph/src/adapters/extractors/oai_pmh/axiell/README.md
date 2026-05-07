# Axiell OAI-PMH Adapter

This adapter ingests records from the Axiell Collections OAI-PMH feed. It extends the shared [OAI-PMH adapter framework](../README.md).

## Configuration

| Setting         | Env var              | Default       | Notes                                      |
| --------------- | -------------------- | ------------- | ------------------------------------------ |
| Metadata prefix | `OAI_METADATA_PREFIX`| `oai_marcxml` | Axiell-specific MARC XML format            |
| Set spec        | `OAI_SET_SPEC`       | `collect`     | Filters to collection records only         |
| Auth header     | —                    | `Token`       | Custom header (not standard Authorization) |
| Window duration | `WINDOW_MINUTES`     | `15`          | Minutes per harvesting window              |
| Lookback        | `WINDOW_LOOKBACK_DAYS`| `7`          | Fallback range (days) when no history      |
| Max lag         | `MAX_LAG_MINUTES`    | `360`         | Maximum allowed lag before circuit breaker  |

## SSM Parameters

| Parameter                                  | Description                 |
| ------------------------------------------ | --------------------------- |
| `/catalogue_pipeline/axiell/oai_api_token` | Axiell OAI API token        |
| `/catalogue_pipeline/axiell/oai_api_url`   | Axiell OAI-PMH endpoint URL |

## Quick start

See the [OAI-PMH README](../README.md) for detailed step-by-step instructions. Use `--adapter-type axiell`.

```bash
# Trigger
uv run python -m adapters.steps.oai_pmh.trigger --adapter-type axiell --at 2025-11-17T12:15:00Z > /tmp/axiell_event.json

# Loader
uv run python -m adapters.steps.oai_pmh.loader --adapter-type axiell --event /tmp/axiell_event.json

# Reloader (fill gaps)
uv run python -m adapters.steps.oai_pmh.reloader --adapter-type axiell \
  --job-id gap-reload \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

See [`config.py`](config.py) for the full set of environment variables.
