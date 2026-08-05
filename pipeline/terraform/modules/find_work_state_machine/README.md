# find_work state machine

A scheduled scan-and-fan-out state machine for pipeline steps whose work volume
per window is unbounded:

```
FindWork (Lambda: normalise the input, find the ids in scope, partition to S3)
  -> ProcessPartitions (Map: one worker per partition ref, bounded concurrency)
    -> CheckPartitionFailures (fail loudly, or tolerate and succeed)
```

The state machine passes its raw invocation payload straight to the find-work
Lambda, which owns all input handling (`core/find_work.normalise_lambda_input`):
a scheduled run's `scheduled_time` becomes the window end minus an indexing
lag, replay input passes through, and deployment identity (pipeline date, graph
date, index dates) comes from the Lambda's environment. Keeping this in Python
makes the guards unit-testable instead of living as JSONata in the definition.

The find-work Lambda only discovers ids and slices them, so its runtime does not
depend on how much work the window matched. Each partition file on S3 holds the
full event for one worker; the Map iterates small `{s3_uri, count}` refs so its
payload stays under the Step Functions 256 KB state limit. The long-running
processing happens in the caller-injected worker state (an ECS task for the
image inferrer, a Lambda for the id-minter), one bounded partition at a time.

`max_concurrency` is the work-in-progress ceiling. Pin it to the worker's real
capacity rather than picking a number here: the inferrer pins it to the ASG's
`max_instances`, and the id-minter to its RDS connection budget. An INLINE Map
runs at most 40 concurrent iterations, whatever the setting.

## Failure semantics

A partition that fails after the worker's own retries is caught and recorded,
so every other partition still runs. The Map output carries the aggregate
counts and the failure records only (successful outputs are dropped, so the
output cannot grow with partition count towards the 256 KB state limit):

```json
{"partition_count": 34, "failed_partition_count": 1, "failed_partitions": [{"partition_failed": true, "s3_uri": "...", "error": "..."}]}
```

What happens next depends on `tolerate_partition_failures`:

- `true`, used by the image inferrer: the execution succeeds. This is only safe
  when a missed record stays recoverable, because replaying the same window
  later re-covers it idempotently (scheduled windows tile with no overlap, so
  the next one does not). The execution succeeding means nothing alarms, so the
  consumer also needs its own failure signal (see Retry and alerting).
- `false`, used by the id-minter: once every partition has finished, the
  execution fails with `PartitionsFailed`. A tolerated skip would leave the
  missed records unprocessed with nothing to notice them, so the execution
  fails loudly instead; failing after the Map means a replay only needs to
  cover the failed partitions, not the whole window.

Workers must keep their own outputs small too: the 256 KB limit applies to each
task result before any projection, so a worker that echoes its input ids back
would fail on dense partitions after doing all its work.

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
- `ids`, a JSON array of ids to process instead of a window; `source_identifiers`
  is accepted as an alias, matching the id-minter's older replay shape.
- `job_id`, honoured by consumers that stamp per-run reports: the id-minter
  derives per-partition job ids from it (`<job_id>-p000`, `-p001`, ...), which
  keys its S3 reports. The inferrer's find-work event has no `job_id` field and
  silently ignores it.
- `partition_size`, to override the consumer's default ids-per-partition.
- `full: true`, required to run with no ids and no window; without it an
  unscoped invoke fails rather than scanning the whole index.

Replays are safe to repeat because both consumers process idempotently, and the
partition files are keyed by scope (window, ids or full), so re-running the
same input overwrites the previous run's files rather than accumulating. The
flip side is that two concurrent replays of the same scope share the same
partition files and will trample each other; run same-scope replays one at a
time.

## Replaying only the failed partitions

Each record in the Map output's `failed_partitions` array carries the failed
partition's `s3_uri` and a truncated `error`, so the execution output alone
identifies what failed and why. The files live under the consumer's
scope-keyed prefix, for example:

```
s3://wellcomecollection-catalogue-graph/graph-<graph_date>/pipeline-<date>/<service>/find_work/windows/<start>-<end>/partition-<i>.json
```

Each file contains the ids for that partition, so a targeted replay is an
`ids`-mode execution built from the failed files' contents.
