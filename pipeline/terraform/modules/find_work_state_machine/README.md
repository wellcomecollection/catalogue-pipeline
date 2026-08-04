# find_work state machine

A scheduled scan-and-fan-out state machine for pipeline steps whose work volume
per window is unbounded:

```
ConstructEvent (build the window from the schedule, or pass through replay input)
  -> FindWork (Lambda: ids in scope, partitioned into files on S3)
    -> ProcessPartitions (Map: one worker per partition ref, bounded concurrency)
      -> CheckPartitionFailures (fail loudly, or tolerate and succeed)
```

The find-work Lambda only discovers ids and slices them, so its runtime does not
depend on how much work the window matched. Each partition file on S3 holds the
full event for one worker; the Map iterates small `{s3_uri, count}` refs so its
payload stays under the Step Functions 256 KB state limit. The long-running
processing happens in the caller-injected worker state (an ECS task for the
image inferrer, a Lambda for the id-minter), one bounded partition at a time.

`max_concurrency` is the work-in-progress ceiling. Pin it to the worker's real
capacity rather than picking a number here: the inferrer pins it to the ASG's
`max_instances`, and the id-minter (moving onto this module in
wellcomecollection/platform#6486) to its RDS connection budget. An INLINE Map
runs at most 40 concurrent iterations, whatever the setting.

## Failure semantics

A partition that fails after the worker's own retries is caught and recorded,
so every other partition still runs. The Map output carries the aggregate:

```json
{"partition_count": 34, "failed_partition_count": 2, "results": [...]}
```

What happens next depends on `tolerate_partition_failures`:

- `true` (image inferrer): the execution succeeds. This is only safe when a
  later scheduled window re-covers the same records idempotently.
- `false` (the id-minter, once adopted): once every partition has finished, the
  execution fails with `PartitionsFailed`. Nothing re-covers a missed window for
  these consumers, so a silent skip would be data loss; failing after the Map
  means a replay only needs to cover the failed partitions, not the whole
  window.

## Retry and alerting

Retries happen at the worker state only, through the caller-injected `Retry`
policies. They should cover transient infrastructure (plus `Lambda.Unknown` for
Lambda workers, so a function timeout gets a re-run); re-running a whole
partition is safe because consumers process idempotently. Once those retries
exhaust, the partition is recorded and not retried again within the execution.
Application failures such as a poisoned document would loop forever if retried
blindly, and the ASL layer cannot tell them apart from stubborn transients, so
recovery from recorded failures is a deliberate, human-started replay.

Do not redrive a failed execution. The Map state itself never fails (the catch
records partition failures), so a redrive restarts from `FailPartitions`,
re-evaluates the same aggregate and fails again. Replay with a fresh
`StartExecution` as described below.

Alerting rides on the module's state machine alarms (`ExecutionsFailed`,
`ExecutionsAborted`, `ExecutionsTimedOut`, threshold 0, wired to the chatbot
topic). With `tolerate_partition_failures = false`, a lost partition fails the
execution and therefore alerts. With `true`, tolerated failures do not alarm;
the aggregate counts sit in the execution output but nothing consumes them
automatically, so consumers in that mode need their own signal for failure
classes that matter (the inferrer alarms on its `download_failure_count`
metric).

## Replaying

Scheduled runs process the window `[scheduled_time - 20min, scheduled_time - 5min]`.
To replay a lost or failed range, start an execution with explicit input instead:

```sh
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:eu-west-1:760097843905:stateMachine:pipeline-<date>_<name> \
  --name <replay-name> \
  --input '{"job_id": "replay-s01", "window": {"start_time": "2026-07-30T16:32:00Z", "end_time": "2026-07-30T16:34:00Z"}}'
```

Accepted input fields, all guarded against malformed shapes (a `window: null`
slip fails validation in the Lambda rather than scanning the full index):

- `window`, with `start_time` optional (defaults to `end_time` minus 15
  minutes). Windows slice to arbitrary timestamps, so a dense range can be
  replayed as several smaller executions.
- `ids`, a JSON array of ids to process instead of a window.
- `job_id`, honoured by consumers that stamp per-run reports (the id-minter,
  whose generated ids are minute-granular, so concurrent replays started in the
  same minute collide without one). The inferrer's find-work event has no
  `job_id` field and silently ignores it.
- `partition_size`, to override the consumer's default ids-per-partition.

Replays are safe to repeat because both consumers process idempotently, and the
partition files are keyed by scope (window, ids or full), so re-running the
same input overwrites the previous run's files rather than accumulating. The
flip side is that two concurrent replays of the same scope share the same
partition files and will trample each other; run same-scope replays one at a
time.

## Replaying only the failed partitions

Each failure record in the Map output's `results` array carries the failed
partition's `s3_uri` and a truncated `error`, so the execution output alone
identifies what failed and why. (The array also preserves partition order, so
positional index works as a fallback.) The files live under the consumer's
scope-keyed prefix, for example:

```
s3://wellcomecollection-catalogue-graph/graph-<graph_date>/pipeline-<date>/<service>/find_work/windows/<start>-<end>/partition-<i>.json
```

Each file contains the ids for that partition, so a targeted replay is an
`ids`-mode execution built from the failed files' contents.
