# Axiell OAI-PMH Adapter

This adapter ingests records from the Axiell Collections OAI-PMH feed in 15-minute windows, persists raw payloads in Iceberg, and emits transformed records into the catalogue graph search indices.

## Environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `AWS_REGION` | `eu-west-1` | Region for AWS clients and S3 Tables REST catalog |
| `AWS_ACCOUNT_ID` | _required in Lambda_ | Account hosting the Iceberg tables |
| `AXIELL_S3_TABLES_BUCKET` | `wellcomecollection-platform-axiell-adapter` | Bucket backing the S3 Tables REST catalog |
| `REST_API_NAMESPACE` | `wellcomecollection_catalogue` | Iceberg namespace for harvested records |
| `REST_API_TABLE_NAME` | `axiell_adapter_table` | Iceberg table storing harvested records |
| `WINDOW_STATUS_NAMESPACE` | `axiell_window_status` | Namespace for the window status tracking table |
| `WINDOW_STATUS_TABLE` | `window_status` | Table name for window status tracking |
| `WINDOW_STATUS_CATALOG_NAME` | `axiell_window_status_catalog` | Catalog identifier for the window status table |
| `AXIELL_S3_BUCKET` | `wellcomecollection-platform-axiell-adapter` | Bucket for manifests, payloads, and tracking files |
| `S3_PREFIX` | `dev` | Root prefix inside `AXIELL_S3_BUCKET` for all artefacts |
| `OAI_SET_SPEC` | `collect` | OAI-PMH set to harvest |
| `OAI_METADATA_PREFIX` | `oai_raw` | Metadata prefix requested from the source |
| `SSM_OAI_TOKEN` | `/catalogue_pipeline/axiell_collections/oai_api_token` | Parameter Store path for the API token |
| `SSM_OAI_URL` | `/catalogue_pipeline/axiell_collections/oai_api_url` | Parameter Store path for the OAI endpoint |
| `WINDOW_MINUTES` | `15` | Length of each harvesting window |
| `WINDOW_LOOKBACK_DAYS` | `3` | Default lookback when searching for pending windows |
| `WINDOW_MAX_PARALLEL_REQUESTS` | `3` | Max concurrency when fetching windows |
| `MAX_LAG_MINUTES` | `60` | Hard limit for trigger → loader lag before alerting |
| `EVENT_BUS_NAME` | `catalogue-pipeline-events` | EventBridge bus for adapter events |
| `TRIGGER_DETAIL_TYPE` | `AxiellWindowRequested` | Detail type for trigger → loader events |
| `LOADER_DETAIL_TYPE` | `AxiellWindowLoaded` | Detail type used post-load |
| `PIPELINE_DATE` | `dev` | Pipeline label embedded in manifests |
| `ES_API_KEY_NAME` | `axiell-transformer` | SSM/Secrets Manager name for the Elasticsearch API key |
| `ES_INDEX_NAME` | `axiell-works-dev` | Target Elasticsearch index for transformed documents |
| `ES_MODE` | `private` | Elasticsearch connectivity mode (`private`, `public`, or `local`) |

These defaults align with `current_task.md`. Override them per environment through Lambda configuration or Terraform variables.
