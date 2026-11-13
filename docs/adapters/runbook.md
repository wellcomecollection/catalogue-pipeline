# Catalogue pipeline adapters runbook

The adapters copy records from our source catalogues into stores the catalogue pipeline reads, so that new and edited records reach collections search and works pages on wellcomecollection.org.

- **Backplane:** [catalogue-pipeline](https://backplane.wellcome.digital/catalog/system/default/catalogue-pipeline) (internal). The owning team, its contact channel and any suppliers sharing the service are on that page.
- **Code:** [sierra_adapter](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/sierra_adapter), [mets_adapter](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/mets_adapter), [tei_adapter](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/tei_adapter), [calm_adapter](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/calm_adapter), and the EBSCO adapter in [catalogue_graph](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/catalogue_graph/src/adapters)
- **Last reviewed:** 2026-11-13, first draft

## What it is

Each adapter is a singleton in the platform AWS account (eu-west-1). It is deployed once and feeds every active dated pipeline, so an adapter fault affects production whichever pipeline is live. When an adapter stops, nothing breaks for users straight away: search keeps serving what is already indexed, but edits and new records stop appearing.

| Adapter | Source | Runs as | Notes |
|---|---|---|---|
| Sierra | Sierra library system (hosted by Innovative) | Window generator and reader Lambdas per record type (bibs, items, holdings, orders), then linker, merger and indexer ECS services in cluster `sierra-adapter-20200604` | Window generators fire every minute for a 2 minute window |
| METS | Storage service bag notifications | ECS service `mets-adapter` in cluster `mets-adapter`, scaled from zero | |
| TEI | The [wellcome-collection-tei](https://github.com/wellcomecollection/wellcome-collection-tei) GitHub repository | `tei_updater` Lambda polling GitHub, then `tei_id_extractor` and `tei_adapter` ECS services in cluster `tei-adapter` | |
| CALM | CALM archive catalogue | ECS services in cluster `calm-adapter`, all scaled to zero | Harvesting has been off since 2026-09-10 ([calm_adapter/README.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/calm_adapter/README.md)) |
| EBSCO | EBSCO FTP service (e-journals) | Step Functions state machine `ebsco-adapter`, run daily at 02:00 UTC by EventBridge Scheduler | The Axiell and FOLIO adapters use the same module and raise the same alarms |

| | |
|---|---|
| Runs in | platform account, eu-west-1, the clusters, Lambdas and state machine above |
| Public URL or entry point | None, queue and schedule driven |
| Users affected if down | New and edited catalogue records stop reaching collections search and works pages. Nothing already indexed is lost |
| Statuspage component | Collections Search, only if the delay is long enough for users to notice. A Sierra outage that breaks sign-in is Requesting & Sign-in |

## How to tell it is broken

The alarms come from shared modules rather than per-adapter files: queue and DLQ alarms from the [terraform-aws-sqs](https://github.com/wellcomecollection/terraform-aws-sqs) `queue` module through [infrastructure/modules/scaling_service](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/infrastructure/modules/scaling_service), Lambda error alarms from [infrastructure/modules/lambda](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/infrastructure/modules/lambda/lambda_function.tf), and EBSCO alarms from [catalogue_graph/infra/adapters/modules/adapter/cloudwatch.tf](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/catalogue_graph/infra/adapters/modules/adapter/cloudwatch.tf). Alerts in #wc-platform-alerts are triaged by the [bug nurse](https://github.com/wellcomecollection/developer-handbook/blob/main/incident-management/bug-nurse.md).

| Signal | Posts to | What it means | Go to |
|---|---|---|---|
| `<queue>_dlq_not_empty`, for example `sierra-adapter-20200604-sierra_items_merger_input_dlq_not_empty` or `tei-adapter_dlq_not_empty` | #wc-platform-alerts | A message failed four times (ten for the Sierra linkers) and was parked. Those records are not updated until it is redriven | [An adapter DLQ is not empty](#an-adapter-dlq-is-not-empty) |
| `lambda-sierra-adapter-20200604-sierra-reader-<type>-errors` | #wc-platform-alerts | A Sierra reader invocation failed. Usually retried and recovered without help | [Sierra reader errors and timeouts](#sierra-reader-errors-and-timeouts) |
| sierra-reader bot message "There are gaps in the ... data" | #wc-platform-alerts | Some Sierra windows were never read, so edits made then are missing | [Gaps in Sierra data](#gaps-in-sierra-data) |
| `lambda-sierra_<type>_window_generator-errors`, `lambda-tei_updater-errors`, `lambda-sierra-adapter-20200604-sierra_progress_reporter-errors` | #wc-platform-alerts | A scheduled Lambda failed. A failed window generator means windows were never sent and shows up later as gaps. A failed progress reporter means gaps may go unreported | [Gaps in Sierra data](#gaps-in-sierra-data) |
| `ebsco-adapter-failed`, `ebsco-adapter-timeout`, `ebsco-adapter-aborted` | #wc-platform-alerts | The nightly EBSCO run did not finish | [EBSCO run failed](#ebsco-run-failed) |
| `<queue>_age`, for example `mets_adapter_queue_age` | Nowhere | The oldest message has waited over 6 hours. These alarms have no action, so check them by hand | [Messages are piling up](#messages-are-piling-up) |
| `catalogue-<date>_transformer_<source>_input_age` | #wc-platform-alerts | The first pipeline stage after the adapters is backed up, often from an adapter flood | [Messages are piling up](#messages-are-piling-up) |
| Reports from colleagues or the public | #wc-platform-feedback | "I edited this record in Sierra and it is not showing" | [An edit is not showing](#an-edit-is-not-showing) |

Quick checks anyone can run with the read-only role:

- Alarms in ALARM for the adapters, leaving out the `_high` and `_low` scaling alarms, which sit in ALARM normally: `aws cloudwatch describe-alarms --state-value ALARM --query "MetricAlarms[?!ends_with(AlarmName,'_low') && !ends_with(AlarmName,'_high') && (contains(AlarmName,'adapter') || contains(AlarmName,'sierra') || contains(AlarmName,'tei'))].AlarmName"`
- Sierra API reachability: the `third_party_probes` CloudWatch namespace has `RunSuccess` and `Duration` for `Vendor=sierra` and `Vendor=ebsco_ftp` every 5 minutes.
- Logs: Lambda logs are in CloudWatch under `/aws/lambda/<function name>`. ECS adapter logs go to the logging cluster's Kibana (internal), filtered by service name.

## First steps

1. **Is it real?** Run the quick checks above. A single reader error that recovers is expected (see below); say so in the alert thread.
2. **Say you are looking.** Reply in the alert thread, or in #wc-incident-response if users are affected.
3. **Did something change?** Look at recent [Buildkite builds](https://buildkite.com/wellcomecollection/catalogue-pipeline-deploy-adapters) (internal), the [Catalogue pipeline: Build and Test runs on main](https://github.com/wellcomecollection/catalogue-pipeline/actions/workflows/catalogue-graph-ci.yml?query=branch%3Amain), which call the deploy workflow, and [merged PRs](https://github.com/wellcomecollection/catalogue-pipeline/pulls?q=is%3Apr+is%3Amerged). Every merge to `main` deploys the adapters straight to production with no gate. If a deploy lines up with the start of the problem, roll back first and investigate after (see [Rollback](#rollback)).
4. **Is a dependency down?** Check the probes above and the table in [Dependencies](#dependencies-and-supplier-escalation).
5. **Get access.** Start with `platform-read_only`; the [guide to selecting a role in the CLI](https://github.com/wellcomecollection/aws-account-infrastructure/blob/main/docs/how-to/how-to-select-a-role-in-the-cli.md) (internal) explains how. Redrives, window replays, reindexes and redeploys need `platform-developer`; use it only for the fix itself.
6. **Is it major?** Adapter faults delay updates rather than break the site, so they are rarely major on their own. If records are missing or wrong on the site in a way users notice and a fix is not in sight, follow the [major incident playbook](https://github.com/wellcomecollection/developer-handbook/blob/main/incident-management/incident-comms-and-escalation.md).

The commands below assume:

```console
export AWS_PROFILE=platform-read_only AWS_REGION=eu-west-1
```

## Common failures and fixes

### Sierra reader errors and timeouts

- **Symptom:** a `lambda-sierra-adapter-20200604-sierra-reader-<type>-errors` alarm, or a steady trickle of reader errors with no gap report.
- **Confirm:** the reader Lambdas have 128 MB of memory and a 900 second timeout. In the 30 days to 2026-11-13 each of the four readers logged between 918 and 1,070 errors (about 30 a day), the longest invocation of each hit the 900 second limit, and the bibs reader regularly reported 127 to 128 MB of memory used. To see recent timeouts for one reader in Logs Insights on `/aws/lambda/sierra-adapter-20200604-sierra-reader-bibs`:

  ```text
  filter @type = "REPORT" | parse @message /Status: (?<status>\w+)/ | stats count(*) by status, bin(1h)
  ```

- **Cause:** a reader that stalls on a slow Sierra response runs until the timeout, and the readers run at their memory limit. In the 48 hours to 12:00 UTC on 2026-11-13, 31 of the bibs reader's 57 errors were timeouts and one was a function error; the cause of the rest has not been identified. Lambda retries a failed window twice, and the reader DLQs (`lambda-sierra-adapter-20200604-sierra-reader-<type>_dlq`) were empty on 2026-11-13, so the retries normally recover the window.
- **Fix:** none needed while the retries succeed and no gap report appears. If errors jump well above about 30 a day per reader, treat it as [Sierra is slow or down](#sierra-is-slow-or-down). If the reader DLQs hold messages, those windows were lost: fill them as in [Gaps in Sierra data](#gaps-in-sierra-data).
- **Watch out:** the error alarm does not reliably fire for these timeouts (it stayed OK from 2026-11-07 to 2026-11-13 while the errors continued), so a quiet alarm does not mean a healthy reader.
- **Seen in:** this is a known, recurring condition rather than an incident.

### Gaps in Sierra data

- **Symptom:** the sierra-reader bot posts "There are gaps in the bibs/items data" with a list of missing time ranges. The progress reporter runs every 4 hours and only reports a gap once it is more than 6 hours old.
- **Confirm:** run the replay script in dry-run mode from a checkout of `main`. It assumes `platform-developer` from your current credentials but publishes nothing:

  ```console
  cd sierra_adapter
  uv run python build_missing_windows.py --dry-run
  ```

- **Cause:** every retry of a reader window failed, nearly always because the Sierra API was slow or down at the time.
- **Fix:** once Sierra is answering normally (check the probes), publish the missing windows. The script asks for confirmation and paces publishes at one a second:

  ```console
  uv run python build_missing_windows.py
  ```

  Then react to the bot message with :recycle: so the next bug nurse knows it was re-run.
- **Watch out:** replaying a long gap sends a burst of Sierra changes through the pipeline, so check the transformer queues afterwards.
- **Seen in:** sierra-reader gaps on 2026-09-29 after the Sierra slowdown of 2026-09-28, [#wc-platform-alerts thread](https://wellcome.slack.com/archives/CQ720BG02/p1790660939194439) (internal).

### Sierra is slow or down

- **Symptom:** reader errors rise across all four record types at once, often alongside Auth0 "Failed Login" alerts, because sign-in looks patrons up in Sierra too. The `third_party_probes` metrics for `Vendor=sierra` show timeouts or failed runs.
- **Confirm:** the probe metrics, or the reader logs showing `ConnectTimeout` or `ReadTimeout`.
- **Cause:** the hosted Sierra service. We cannot fix it ourselves.
- **Fix:** escalate through the Sierra route in [Dependencies](#dependencies-and-supplier-escalation). When Sierra recovers, fill any gaps as in [Gaps in Sierra data](#gaps-in-sierra-data).
- **Watch out:** if sign-in is failing for users, this is a user-facing incident for the website and identity, not only an adapter delay. Follow the [major incident playbook](https://github.com/wellcomecollection/developer-handbook/blob/main/incident-management/incident-comms-and-escalation.md).
- **Seen in:** 2026-09-28 Sierra patron lookup slowdown, [#wc-platform-alerts thread](https://wellcome.slack.com/archives/CQ720BG02/p1790613986740659) (internal); the outage of 2026-10-14 from 19:04 to 19:24 UTC, which was first noticed through reader failures.

### An adapter DLQ is not empty

- **Symptom:** a `<queue>_dlq_not_empty` alarm for an adapter queue.
- **Confirm:** count and look at the messages. `receive-message` hides the messages it returns for the visibility timeout but does not delete them:

  ```console
  Q=sierra-adapter-20200604-sierra_items_merger_input   # the queue name without _dlq
  DLQ_URL=$(aws sqs get-queue-url --queue-name "${Q}_dlq" --query QueueUrl --output text)
  aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names ApproximateNumberOfMessages
  aws sqs receive-message --queue-url "$DLQ_URL" --max-number-of-messages 5 --visibility-timeout 30
  ```

  Then find the matching error in the service's logs in Kibana (internal) by record id.
- **Cause:** a message that failed four receives (ten for the Sierra linkers). Possible causes include a record the adapter cannot parse, a store or the reporting cluster being unavailable for a while, or a bug in a recent deploy.
- **Fix:** fix the cause first (roll back a bad deploy, wait for the store to recover). Then move the messages back to the source queue with the developer role. With no destination given, SQS returns them to the queue they came from:

  ```console
  DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query Attributes.QueueArn --output text)
  AWS_PROFILE=platform-developer aws sqs start-message-move-task --source-arn "$DLQ_ARN"
  ```

- **Watch out:** a message that fails because of its data will go straight back to the DLQ after the same number of attempts; open an issue for the record rather than redriving it again. TEI deletes are held back on purpose (2 minutes in `tei_adapter`, 30 minutes in `tei_id_extractor`), and each queue's visibility timeout must stay longer than its delay, or deletes loop until they land in the DLQ ([tei_adapter/terraform/locals.tf](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/tei_adapter/terraform/locals.tf)). For pipeline DLQs after the adapters, read [Redriven merges miss ingest windows](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/reindex-recovery.md#redriven-merges-miss-ingest-windows) before redriving.
- **Seen in:** first draft; no retro yet.

### Messages are piling up

- **Symptom:** an adapter's `<queue>_age` alarm in ALARM (visible only in the console), a transformer input age alert, or a report that edits are slow to appear.
- **Confirm:** the age and depth of the queue, and whether its service has running tasks:

  ```console
  URL=$(aws sqs get-queue-url --queue-name mets_adapter_queue --query QueueUrl --output text)
  aws sqs get-queue-attributes --queue-url "$URL" --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible
  aws ecs describe-services --cluster mets-adapter --services mets-adapter --query "services[].[runningCount,desiredCount]"
  ```

- **Cause:** the adapter services scale on queue depth, and some (METS, for example) scale from zero, so a backlog with no running tasks points at a task that cannot start or a scaling problem. Past floods came from the overnight CALM to Sierra sync touching hundreds of thousands of records after a transformer change made them all look different.
- **Fix:** for tasks that cannot start, check the ECS service events and recent deploys, and roll back if needed. For a flood, see [The queues are flooded](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/troubleshooting/flooded-queues.md).
- **Watch out:** SQS keeps messages for four days and then deletes them without moving them to a DLQ. Anything left on an adapter queue over a long weekend can be lost before it reaches the adapter store. A [specific reindex](#an-edit-is-not-showing) only rereads the store, so first re-fetch those records from the source system, then reindex them. The CALM queues are never read while harvesting is off, so messages sent to them always expire this way.
- **Seen in:** [2022-08-20 slower catalogue searches](https://github.com/wellcomecollection/docs/blob/main/incident_retros/2022-08-20_slower-catalogue-searches.md) and [2022-10-04 slow search from 900k messages on the ingestor queue](https://github.com/wellcomecollection/docs/blob/main/incident_retros/2022-10-04_slow-search-due-to-900k-messages-on-the-ingestor-queue.md), both from the CALM to Sierra sync; [2024-10-07 updates not getting through to works](https://github.com/wellcomecollection/docs/blob/main/incident_retros/2024-10-07_updates_not_getting_through_to_works.md), where a stuck queue went unnoticed for two days.

### EBSCO run failed

- **Symptom:** `ebsco-adapter-failed`, `-timeout` or `-aborted` in #wc-platform-alerts.
- **Confirm:** open the latest execution of the `ebsco-adapter` state machine in the Step Functions console and find the failed step. The `third_party_probes` metrics for `Vendor=ebsco_ftp` show whether the FTP service was reachable.
- **Cause:** the FTP service being unavailable, or a fault in a step.
- **Fix:** once the cause is fixed, start a fresh execution with the same input the schedule uses. Do not use Step Functions redrive, which resumes a stale execution with stale inputs ([reindex-recovery.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/reindex-recovery.md#rules-that-hold-everywhere)):

  ```console
  ARN=$(aws stepfunctions list-state-machines --query "stateMachines[?name=='ebsco-adapter'].stateMachineArn" --output text)
  AWS_PROFILE=platform-developer aws stepfunctions start-execution --state-machine-arn "$ARN" --input '{"adapter_type": "ebsco"}'
  ```

  Missing one night is not urgent. Each run compares the whole incoming EBSCO file with the stored records, so the next scheduled run also covers the missed night.
- **Watch out:** the same alarms exist for `axiell-adapter` and `folio-adapter`, which run every 15 minutes; their harvest specifics are in the [OAI-PMH README](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/catalogue_graph/src/adapters/extractors/oai_pmh/README.md).
- **Seen in:** first draft; no retro yet.

### An edit is not showing

- **Symptom:** a colleague reports that a change made in Sierra (or a new METS or TEI file) is not on the website.
- **Confirm:** check whether the adapter has the change. For Sierra, from `sierra_adapter`, `uv run python get_sierra_transformable_record.py b1234567` saves the stored record to a temporary JSON file, prints its path and opens it; compare its modified dates with the edit. If the adapter has it, the delay is in the pipeline, not the adapter.
- **Cause:** a gap in the Sierra windows, a message that expired or sits in a DLQ, or a pipeline fault downstream.
- **Fix:** if the adapter is missing the change, fill gaps or redrive as above. If the adapter has it, send the record through the pipeline again with a specific reindex from [reindexer/scripts](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/reindexer/scripts). Sierra ids are the seven-digit form, without the `b` prefix or check digit:

  ```console
  cd reindexer/scripts
  uv run start_reindex.py --src sierra --dst catalogue --mode specific
  ```

- **Watch out:** a complete reindex of a source is a planned operation, not an incident fix. Follow [REINDEXING.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/REINDEXING.md) and [Recovering a reindex](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/reindex-recovery.md).
- **Seen in:** [2024-10-07 updates not getting through to works](https://github.com/wellcomecollection/docs/blob/main/incident_retros/2024-10-07_updates_not_getting_through_to_works.md).

## Rollback

Follow the general [rollback guide](https://github.com/wellcomecollection/developer-handbook/blob/main/developer-handbook/rollback.md) first. The [change model](https://github.com/wellcomecollection/developer-handbook/blob/main/developer-handbook/change-model.md) says which kinds of change need which route. For the adapters:

- **How it deploys:** a merge to `main` runs Buildkite, which publishes each Scala image with its commit SHA and `latest`, then the [deploy-adapters pipeline](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/.buildkite/pipeline.deploy-adapters.yml) moves `env.prod` to `latest`, redeploys the ECS services and the Sierra reader, progress reporter and TEI updater Lambdas. The EBSCO, Axiell and FOLIO adapters are deployed by the GitHub Actions workflow `Catalogue pipeline: Deploy`, which moves the `prod` tag on the unified pipeline images. [deployment.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/deployment.md) has the detail.
- **To roll back a code change:** revert the PR. The revert deploys through the same route. To put an ECS adapter back faster, point `env.prod` at the previous commit's image and redeploy, with `platform-developer`, from a checkout of the repo. Images are tagged with the full 40-character commit SHA:

  ```console
  export AWS_PROFILE=platform-developer
  REPO=uk.ac.wellcome/sierra_merger   # repeat for each image in the adapter
  MANIFEST=$(aws ecr batch-get-image --repository-name "$REPO" --image-ids imageTag=PREVIOUS_COMMIT_SHA --query "images[0].imageManifest" --output text)
  aws ecr put-image --repository-name "$REPO" --image-tag env.prod --image-manifest "$MANIFEST"
  CLUSTER=sierra-adapter-20200604 ./builds/deploy_ecs_services.sh SERVICE_NAMES
  ```

  The image and service names for each adapter are in the deploy-adapters pipeline file. ECR keeps the images an `env.` tag points at and only the ten newest other builds, so an older commit may need a revert instead.
- **Cannot be rolled back this way:** records already written to the adapter stores (deploy the fix, then re-fetch the affected records from the source system or repair them in the store, then reindex them; a reindex alone replays the bad version); Terraform changes, which go back through a revert PR and a plan and apply from `main` (see the [rollback guide](https://github.com/wellcomecollection/developer-handbook/blob/main/developer-handbook/rollback.md#terraform), including when it cannot wait); the CALM `harvesting_enabled`, `deletion_checking_enabled` and `indexing_enabled` flags. Do not dispatch `Catalogue pipeline: Deploy` with an older SHA to roll back EBSCO: it also moves every dated pipeline it reaches, production included.
- **Check it worked:** `aws ecs describe-services --cluster CLUSTER --services SERVICE_NAMES --query "services[].deployments[].[status,rolloutState,taskDefinition]"` shows one completed deployment, and the queue starts draining.

## Dependencies and supplier escalation

Suppliers who build or run part of this service are listed as vendors on the [Backplane entity](https://backplane.wellcome.digital/catalog/system/default/catalogue-pipeline) (internal). Their support contacts, contract terms and severity definitions are in [suppliers.md](https://github.com/wellcomecollection/private/blob/main/suppliers.md) (private).

| Dependency | What breaks if it is down | How to check it | Who escalates, and how |
|---|---|---|---|
| Sierra (Innovative) | Sierra edits stop; patron sign-in also fails | `third_party_probes`, `Vendor=sierra`. Sierra has no status page | We do not contact Innovative directly. Tell Collections Product, who raise it with Innovative, as in the [major incident playbook](https://github.com/wellcomecollection/developer-handbook/blob/main/incident-management/incident-comms-and-escalation.md) |
| EBSCO FTP | EBSCO e-journal records stop updating | `third_party_probes`, `Vendor=ebsco_ftp` | Not yet agreed |
| Storage service | New digitised and born-digital items stop reaching the METS adapter | Storage service alarms in #wc-platform-alerts | Owning team, via its Backplane team page |
| GitHub | TEI updates stop | [githubstatus.com](https://www.githubstatus.com) | Wait for recovery; the TEI updater catches up on its next poll |
| Reporting cluster (Elastic Cloud) | The Sierra indexer fails and its DLQ fills; the catalogue itself is not affected | Elastic Cloud console | Raise a support ticket in the Elastic Cloud console, as in the [major incident playbook](https://github.com/wellcomecollection/developer-handbook/blob/main/incident-management/incident-comms-and-escalation.md) |
| Dated catalogue pipeline | Adapter output queues up at the transformers | [Recovering a reindex](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/reindex-recovery.md), [deployment.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/deployment.md) | Same team |

## Related links

- Architecture pages: [sierra_adapter](https://github.com/wellcomecollection/docs/blob/main/architecture/services/catalogue_pipeline/sierra_adapter.md), [mets_adapter](https://github.com/wellcomecollection/docs/blob/main/architecture/services/catalogue_pipeline/mets_adapter.md), [tei_adapter](https://github.com/wellcomecollection/docs/blob/main/architecture/services/catalogue_pipeline/tei_adapter.md), [calm_adapter](https://github.com/wellcomecollection/docs/blob/main/architecture/services/catalogue_pipeline/calm_adapter.md), [ebsco_adapter](https://github.com/wellcomecollection/docs/blob/main/architecture/services/catalogue_pipeline/ebsco_adapter.md)
- How the Sierra adapter works: [sierra_adapter/README.md](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/sierra_adapter/README.md). Its description of 15 minute schedules and 30 minute windows is out of date; the window generators now run every minute.
- [Fetching records from Sierra](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/adapters/fetching_records_from_sierra.md). Its script names are out of date: use `build_missing_windows.py` as above, or `common/window_generator/src/build_windows.py` for a hand-picked range.
- [The queues are flooded](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/troubleshooting/flooded-queues.md)
- [Reindexing](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/REINDEXING.md) and [Recovering a reindex](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/reindex-recovery.md)
- [Deployment](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/docs/pipeline/deployment.md)
