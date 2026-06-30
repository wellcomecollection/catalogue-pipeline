# Proposed terraform: FOLIO item enrichment (RFC 088 / Option C)

> **Status: proposal, not wired in.** The files here use a `.tf.proposed` suffix so
> terraform does **not** load them. Nothing in this directory affects
> `terraform plan`/`apply` in `infra/adapters`. To adopt, move the resources into the
> parent module (or a new file there) and apply deliberately. Design doc:
> `docs/discovery/folio-oai-pmh-item-enrichment.md`.

Option C gives the public catalogue a stable item id by enriching each harvested
FOLIO instance with item/holdings UUIDs from mod-inventory-storage's
`oai-pmh-view/enrichedInstances`, stored in a second Iceberg table and joined onto
the bib store at transform time. The application code for this already lives in
`catalogue_graph/src/adapters/extractors/oai_pmh/folio/enrichment/` and the
transform-time join.

This proposal covers the three infra pieces that code needs:

1. **`folio_items_table`** — a second Iceberg table in the existing FOLIO S3 Tables
   namespace, same schema as the bib adapter store. See
   `folio_items_table.tf.proposed`.
2. **Inventory API SSM parameters** — URL + token for mod-inventory-storage (distinct
   from the OAI-PMH feed credentials). See `inventory_ssm.tf.proposed`.
3. **"Run enrichment" state + enrichment ECS task** — a new state in the per-adapter
   Step Functions machine, between *Run loader* and *Publish event*, plus the ECS
   task that runs it. See `enrichment_state.tf.proposed` and
   `state_machine_diff.md`.

## How the runtime config maps to these resources

| Code (`folio/config.py`)        | Resource                                  |
| ------------------------------- | ----------------------------------------- |
| `ITEMS_REST_API_TABLE_NAME`     | `folio_items_table` Iceberg table         |
| `SSM_INVENTORY_URL`             | `/catalogue_pipeline/folio/inventory_api_url`   |
| `SSM_INVENTORY_TOKEN`           | `/catalogue_pipeline/folio/inventory_api_token` |
| `FOLIO_INVENTORY_TENANT` (env)  | set on the enrichment ECS task            |

## Sequencing note

Apply the table + SSM params and seed the items store **before** enabling the
enrichment state in the state machine, so the first publish event after cutover has
items rows to join against. Until enrichment runs, `FolioWorkBuilder.items` returns
no items (it does not guess from MARC 952), so works remain valid throughout.
