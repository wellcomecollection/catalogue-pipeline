# Proposed state-machine change: add a "Run enrichment" state

This documents the change to `modules/adapter/state_machine.tf` needed to run
enrichment inside the same execution, before the publish event, so that by the time
`folio.adapter.completed` fires both the bib row and the item row exist for every
changed instance.

Because enrichment is FOLIO-specific, gate it behind a new module variable
(default off) so EBSCO/Axiell are unaffected:

```hcl
# modules/adapter/variables.tf
variable "enable_item_enrichment" {
  type        = bool
  default     = false
  description = "Run a FOLIO item-enrichment state between Run loader and Publish event."
}
```

In the JSONata state machine definition, change `Run loader` to route to a choice,
and add the enrichment state. The loader keeps emitting `changeset_ids`; the
enrichment task reads the changed instance ids from that changeset, calls
`enrichedInstances`, writes `folio_items_table`, and passes both changesets forward.

```
Run loader            -> { changeset_ids: [CS_bib], job_id }
  Next: "Should enrich?"            (only when enable_item_enrichment)

"Should enrich?" (Choice)
  Condition: {% $exists($states.input.changeset_ids[0]) %}  -> "Run enrichment"
  Default:                                                       "Should publish event?"

"Run enrichment" (Task: ecs:runTask.waitForTaskToken)
  TaskDefinition: module.enrichment_ecs_task.task_definition_arn
  Container command:
    "-m", "adapters.steps.oai_pmh.folio_enrich",
    "--event", "{% $string($states.input) %}",
    "--task-token", "{% $states.context.Task.Token %}"
  Output: merges { items_changeset_ids: [CS_items] } into the loader output
  Next: "Should publish event?"
  Retry: [{ ErrorEquals: ["States.ALL"], MaxAttempts: 3, BackoffRate: 2.0 }]
```

`Publish event` then also carries the items changeset for provenance:

```diff
  Detail = {
    transformer_type   = var.namespace
    job_id             = "{% $states.input.job_id %}"
    changeset_ids      = "{% $states.input.changeset_ids %}"
+   items_changeset_ids = "{% $states.input.items_changeset_ids %}"
  }
```

Notes:

- A separate enrichment state (rather than folding the call into the loader task)
  keeps retries isolated: an enrichment failure does not re-harvest the bib window.
- The transformer joins by instance id, so the items changeset id is only carried for
  provenance, not for triggering the re-transform (the bib changeset drives that).
- When `enable_item_enrichment` is false the machine is unchanged: `Run loader` goes
  straight to `Should publish event?` exactly as today.
```
