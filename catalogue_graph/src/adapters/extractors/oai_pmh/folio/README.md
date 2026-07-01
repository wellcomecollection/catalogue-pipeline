# FOLIO OAI-PMH Adapter

This adapter ingests records from the FOLIO OAI-PMH feed. It extends the shared [OAI-PMH adapter framework](../README.md).

## Configuration

| Setting         | Value                 | Notes                               |
| --------------- | --------------------- | ----------------------------------- |
| Metadata prefix | `marc21_withholdings` | MARC21 with holdings information    |
| Set spec        | None                  | Harvests all records (no filtering) |
| Auth header     | `Authorization`       | Standard HTTP auth header           |
| Default window  | 15 minutes            | `FOLIO_WINDOW_MINUTES`              |
| Lookback        | 7 days                | `FOLIO_WINDOW_LOOKBACK_DAYS`        |

## SSM Parameters

| Parameter                                       | Description                                  |
| ----------------------------------------------- | -------------------------------------------- |
| `/catalogue_pipeline/folio/oai_api_token`       | FOLIO OAI API token                          |
| `/catalogue_pipeline/folio/oai_api_url`         | FOLIO OAI-PMH endpoint URL                   |
| `/catalogue_pipeline/folio/inventory_username`  | OKAPI username for item enrichment (mod-inventory-storage) |
| `/catalogue_pipeline/folio/inventory_password`  | OKAPI password for item enrichment (mod-inventory-storage) |
| `/catalogue_pipeline/folio/inventory_api_url`   | mod-inventory-storage base URL (item enrichment) |

## Quick start

See the [OAI-PMH README](../README.md) for detailed step-by-step instructions. Use `--adapter-type folio`.

```bash
# Trigger
uv run python -m adapters.steps.oai_pmh.trigger --adapter-type folio --at 2025-11-17T12:15:00Z > /tmp/folio_event.json

# Loader
uv run python -m adapters.steps.oai_pmh.loader --adapter-type folio --event /tmp/folio_event.json

# Reloader (fill gaps)
uv run python -m adapters.steps.oai_pmh.reloader --adapter-type folio \
  --job-id gap-reload \
  --window-start 2025-12-01T00:00:00Z \
  --window-end 2025-12-02T00:00:00Z \
  --use-rest-api-table
```

## Item enrichment

The FOLIO OAI-PMH feed carries item/holdings data in MARC 952 but **no item UUID**,
so on its own it cannot give the public catalogue a stable per-item id. Item
enrichment closes that gap:

1. A second Iceberg store, `folio_items_table` (namespace `folio-items`), mirrors the
   adapter-store shape and is keyed by instance id. Its `content` is the items and
   holdings (with UUIDs) for that instance.
2. The **enrichment step** (`adapters.steps.oai_pmh.folio_enrich`) runs between the
   loader and the publish event. It reads the changed instance ids from the bib
   changeset, fetches their items/holdings from mod-inventory-storage's
   `oai-pmh-view/enrichedInstances`, and upserts them into the items store. The bib
   changeset is the trigger: a `marc21_withholdings` item/holdings change advances the
   instance's OAI datestamp, so it re-appears in the bib window.
3. At **transform time** the FOLIO transformer joins the items store onto each bib
   record by instance id; `FolioWorkBuilder` emits items carrying a `folio-item`
   source identifier with the inventory UUID. When an instance has not been enriched,
   it emits no items (it never guesses from MARC 952), so works stay valid.

Enrichment is enabled in infra via the `enable_item_enrichment` module variable
(FOLIO only). A full reindex never calls FOLIO; it just joins whatever is already in
the items store, so transformer purity is preserved.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.

### Running enrichment locally

The enrichment step has a local CLI (defaults to local Iceberg tables; pass
`--use-rest-api-table` for S3 Tables). The inventory URL, OKAPI credentials and tenant
are read from `FOLIO_INVENTORY_URL` / `FOLIO_INVENTORY_USERNAME` /
`FOLIO_INVENTORY_PASSWORD` / `FOLIO_INVENTORY_TENANT` when set, otherwise from SSM, so
you can point at a dev or mock endpoint without AWS. The enrichment client logs in to
OKAPI itself (POST `/authn/login`) and refreshes on 401. These are OKAPI inventory
credentials, not the OAI feed token; to read them from SSM use
`AWS_PROFILE=platform-developer`.

```bash
# event is the loader response (or any JSON with job_id + changeset_ids)
echo '{"job_id":"local","changeset_ids":["<bib-changeset-id>"]}' > /tmp/enrich_event.json

FOLIO_INVENTORY_URL=https://<inventory-host> FOLIO_INVENTORY_TENANT=<tenant> \
  FOLIO_INVENTORY_USERNAME=<username> FOLIO_INVENTORY_PASSWORD=<password> \
  uv run python -m adapters.steps.oai_pmh.folio_enrich --use-cli --event /tmp/enrich_event.json
```

To see the joined items end to end, bring up a local Elasticsearch and Kibana with the
shared [`elasticsearch.docker-compose.yml`](../../../../../elasticsearch.docker-compose.yml)
(ES on `:9200`, Kibana on `:5601`) and run the transformer against it:

```bash
docker compose -f elasticsearch.docker-compose.yml up

uv run python -m adapters.steps.transformer --transformer-type folio \
  --changeset-id <bib-changeset-id> --es-mode local
```

The transformed works land in a local index you can browse in Kibana at `localhost:5601`.

## Environment variables

| Variable                     | Default               | Description                                 |
| ---------------------------- | --------------------- | ------------------------------------------- |
| `FOLIO_WINDOW_MINUTES`       | 15                    | Harvesting window duration                  |
| `FOLIO_WINDOW_LOOKBACK_DAYS` | 7                     | Fallback range when no history              |
| `FOLIO_MAX_LAG_MINUTES`      | 360                   | Maximum allowed lag before circuit breaker  |
| `FOLIO_OAI_SET_SPEC`         | None                  | OAI set specification (empty = all records) |
| `FOLIO_OAI_METADATA_PREFIX`  | `marc21_withholdings` | OAI metadata prefix                         |
| `FOLIO_INVENTORY_URL`        | None (SSM)            | mod-inventory-storage base URL override (item enrichment) |
| `FOLIO_INVENTORY_USERNAME`   | None (SSM)            | OKAPI username override (item enrichment) |
| `FOLIO_INVENTORY_PASSWORD`   | None (SSM)            | OKAPI password override (item enrichment) |
| `FOLIO_INVENTORY_TENANT`     | None (SSM)            | OKAPI tenant (x-okapi-tenant) override (item enrichment) |
| `FOLIO_ENRICH_BATCH_SIZE`    | 50                    | Instance ids per `enrichedInstances` request |
