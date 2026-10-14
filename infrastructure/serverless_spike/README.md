# Elastic Cloud Serverless spike

Throwaway stack for [platform#6714](https://github.com/wellcomecollection/platform/issues/6714): a serverless project carrying the real pipeline indices, a comparator index on the hosted 2026-07-03 cluster, PrivateLink into the delta VPC, and a harness Lambda that measures write throughput from where the merger and the ingestors run. Destroy it when the spike reports.

## What it creates

- `modules/es_serverless_project`: an `ec_elasticsearch_project` in `aws-eu-west-1`, a bootstrap API key minted with the creation-time credentials, and the nine-key `es_cluster` object the `elastic_indices` module takes, with port set to the literal 443 and `api_key` set.
- `indices.tf`: `elastic_indices` under pipeline date `2026-10-14-serverless`, so the indices, the `set-indexed-at` pipeline, the per-service keys and the connection secrets exist under the usual names in both accounts. Plus a `bench` key that can write only the spike's own indices.
- `hosted.tf`: `works-denormalised-2026-10-14-serverless-hosted-comparator` on the hosted cluster with the same mapping, and a `bench_hosted` key that writes only that index and reads the 2026-09-30 indices for sampling.
- `network.tf`: an interface endpoint to the serverless endpoint service, a private hosted zone for `private.eu-west-1.aws.elastic.cloud` with a wildcard CNAME, and an `ec_serverless_traffic_filter` on the endpoint. The policy is attached to the project only when `attach_private_policy` is true, because any attached policy rejects unmatched sources with 403. `allowed_ip_cidrs` adds an IP policy for a laptop.
- `harness.tf`: the `es-bench` Lambda on the unified pipeline image in the delta private subnets, with the endpoint security group, the hosted PrivateLink security group and egress through NAT, so one function can hit all four paths.

## Applying

The `elasticstack.serverless` provider authenticates with the bootstrap key, so the project has to exist before anything else can plan:

```
./run_terraform.sh init
./run_terraform.sh plan -target=module.project -out=PLANFILE
./run_terraform.sh plan -out=PLANFILE
```

After the second apply, set the On-demand tier in the console and run a plan to read the `search_lake` values back into the `project` output. The project's `search_lake` is in `ignore_changes`, so the plan will not revert it.

To test the private path, apply once more with `-var attach_private_policy=true`, and with `-var 'allowed_ip_cidrs=["LAPTOP/32"]'` if the laptop still needs access. A laptop without an IP policy should then get 403 from the public endpoint.

## Running the harness

The harness code is `catalogue_graph/src/es_bench`. Deploy it to the spike's function with the dev tag, which touches nothing else:

```
catalogue_graph/scripts/deploy_lambda.sh catalogue-2026-10-14-serverless-es-bench
```

Events come from `events/make_event.py`, which pairs a target (`serverless_public`, `serverless_private`, `hosted_public`, `hosted_private`) with a mode. Source documents always come from the hosted 2026-09-30 indices, so every target writes the same documents.

```
cd catalogue_graph
E=../infrastructure/serverless_spike/events
$E/make_event.py ping --target serverless_private > /tmp/ping.json
uv run python -m es_bench.handler invoke --function-name catalogue-2026-10-14-serverless-es-bench --event-file /tmp/ping.json

$E/make_event.py write_matrix --target serverless_public > /tmp/wm.json
uv run python -m es_bench.handler run --event-file /tmp/wm.json          # from the laptop
uv run python -m es_bench.handler invoke --function-name ... --event-file /tmp/wm.json

$E/make_event.py merger_shape --target serverless_private > /tmp/ms.json
uv run python -m es_bench.handler invoke --function-name ... --event-file /tmp/ms.json --parallel 30
```

`write_matrix` samples real documents by sliced point in time and writes them as `external_gte` bulks across bulk sizes 1 to 100 and concurrency 1 to 60, reporting docs per second, latency percentiles, 429s and retries per cell. `merger_shape` is one cell of 50 concurrent single-document bulks, fanned out over 30 invocations to reproduce the merger. `scroll` reads an index on a 30 minute keep-alive. `load` copies one slice of works-denormalised for the cost check; run it once per slice and resume from the returned `search_after` if an invocation hits its deadline.

Results go under `~/workspace/notes/6714-elastic-cloud-serverless/data/`.
