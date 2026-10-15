# Elastic Cloud Serverless for the FOLIO pipeline environment

Results of the spike in [platform#6714](https://github.com/wellcomecollection/platform/issues/6714), run on 2026-10-14 against a throwaway serverless project in eu-west-1 carrying the real pipeline indices, with the hosted es-cluster-2026-07-03 as the comparator. The six checks from the issue all ran. The stack and harness are under `infrastructure/serverless_spike` and `catalogue_graph/src/es_bench`; the cost side, which carries billing figures, is in the private issue platform-infrastructure#560.

## Recommendation

I think the FOLIO environment should run on a serverless project. Nothing in the pipeline's code needs to change for the store itself, the provider and client stack all work against it, private connectivity works, and the cost estimate in platform-infrastructure#560 puts it below a hosted clone of the pipeline cluster in every month and well below it in a reindex month. The price is write latency: every bulk request takes about 260 ms whatever its size, so stages that write one document at a time run at a fraction of their hosted rate unless they keep more requests in flight. For the FOLIO environment that is a slower reindex, not a failure.

I do not think these results decide production. The search side of the bill under the public API's query load was not measured and is the widest line in the cost estimate. The way forward for that decision is set out at the end.

## What was built

- `infrastructure/serverless_spike/terraform`: an `ec_elasticsearch_project` in aws-eu-west-1, the existing `elastic_indices` module against it under pipeline date `2026-10-14-serverless` (eight indices, the `set-indexed-at` pipeline, sixteen per-service API keys, connection secrets in both accounts), a comparator index with the works-denormalised mapping on the hosted cluster, an interface endpoint, security group and private hosted zone for `private.eu-west-1.aws.elastic.cloud` in the delta VPC, a private connection policy and an operator IP policy, and a harness Lambda on the unified pipeline image in the delta private subnets.
- `catalogue_graph/src/es_bench`: a Python harness that samples real documents by sliced point in time and writes them as `external_gte` bulks, with matrix, merger-shape, scroll, load, count and ping modes, run from the laptop and as the Lambda.
- `common/pipeline_storage/src/test/.../ServerlessBench.scala`: the same write shape through the real `ElasticIndexer` and `ElasticSourceRetriever` on elastic4s 8.9.5.

## Results by check

### 1. Write throughput

Bulk latency on the project is flat at 258 to 290 ms p50 for every bulk size up to ten in flight, the documented 200 ms write floor plus round trip, against 5 to 25 ms on hosted. Throughput scales with bulk size and with in-flight requests, and the project never returned a 429 at request or item level in any run. Measured from the harness Lambda in the delta VPC, private path, 5,000 real documents per cell:

| bulk size, in flight | serverless docs/s | hosted docs/s | factor |
|---|---|---|---|
| 1, 1 | 3.8 | 179 | 47 |
| 1, 10 | 38 | 934 | 25 |
| 10, 10 | 375 | 5,640 | 15 |
| 100, 10 | 3,308 | 10,965 | 3.3 |
| 100, 60 | 7,097 | 13,426 | 1.9 |

The merger's shape, 30 invocations at once each with 50 single-document bulks in flight through a ten-connection pool (the Elasticsearch REST client default the merger runs with): 1,115 docs/s aggregate on serverless against 13,124 on hosted, each serverless invocation pinned at 38 docs/s by ten connections times the floor. At the merger's 30-invocation ceiling that puts a floor of about 46 minutes of merger wall time on a 3.06 million work reindex, against about 4 minutes hosted, before the merger's own reads and merging. Raising `maxConnPerRoute` in scala-libs' `ElasticClientBuilder` from the default 10 to the 50 the merger already has in flight would recover most of that; I have not tested it.

A full copy of works-denormalised (3.06 million documents) from the hosted cluster into the project, in eight parallel Lambda slices with bulks of 500 and four in flight each, ran three times at 15,000 to 17,900 docs/s aggregate, 166 to 206 s each, no rejections, and a random-id check found 100% of the source present afterwards. The transformers already write bulks of up to 100, and the Python ingestors write through `helpers.bulk`, so the stages that matter for a reindex already have the shape that suits serverless.

### 2. Terraform

The `elasticstack` provider at 0.16.1 creates indices, the ingest pipeline and API keys on the project with basic auth using the credentials the project exports at creation. Two things the issue assumed turned out wrong. A request authenticated by an API key cannot create keys that carry privileges (Elasticsearch's derived-key rule), so the bootstrap-key design is dead: Terraform keeps the creation-time credentials, as the hosted module does, and `elastic_indices` needs no provider change, only optional `read_only` fields because serverless has no native realm. And whatever credential the provider uses needs the `monitor` cluster privilege, because it reads the root endpoint before managing each resource.

The serverless endpoints carry no port suffix, so the hosted module's split-on-colon port derivation returns the hostname; the serverless module emits the literal 443. `private_endpoints` is populated at creation. The ec provider at 0.13.0 creates `ec_serverless_traffic_filter` policies of type `vpce` and `ip`, attaches them, and updates `search_lake` in place.

### 3. Clients

elasticsearch-py 8.12.1 (the catalogue_graph pin) and elastic4s 8.9.5 with the REST client 8.9.2 both work unchanged, including `ElasticIndexer`'s `external_gte` bulks and `ElasticSourceRetriever`'s multi-get. elasticsearch-py 9.5.1 also works with the same harness code: an index-level ping, a 30-slice point-in-time sample from the project and bulk writes, with no deprecation warnings, so the 9.x bump is open to the pipeline but not required by serverless.

The real works ingestor ran its indexer step locally against the project (`ingestor.steps.ingestor_indexer --use-cli`, public endpoint, the `works_ingestor` key) over two loader files from the 2026-09-30 full job staged under a spike-only prefix, and indexed 20,000 documents in two batches with no failures.

The catalogue API's multi-cluster path ran locally from a catalogue-api checkout with a `multiCluster.serverless-spike` entry pointing at the spike's `public_host` and `catalogue_api/api_key` secrets, as the multi-cluster guide prescribes for local use. `MultiElasticConfigParser` found the entry, the API built the client, and `?elasticCluster=serverless-spike` returned works pages, a `london` query and a genre aggregation from `works-indexed-2026-10-14-serverless` while a copy of works-indexed was still filling it. The entry was not deployed to stage or production.

### 4. Reads

A 30-slice point in time (the `core/source.py` constant) returned a complete sample from the project, and scroll on a 30 minute keep-alive read 134,160 documents at 14,000 docs/s, so the snapshot generator can keep scroll.

### 5. Cost

The estimate, the year's hosted cost lines it is set against, and the one measured serverless anchor are in platform-infrastructure#560, which is private because it carries billing figures. The project is now at search power 28, the API floor, because the console did not offer the On-demand, Performant and High availability labels for it, so the tier-to-integer mapping the issue asked for could not be read. Elastic's billing API itemises the project's usage per day. Idle at search power 28 with about 4.4 GB retained, it used about 0.039 search VCU an hour (2026-10-15, 00:00 to 10:00 UTC), and the day of the load tests used 10.5 ingest VCU-hours for roughly 9 million documents written, about 1.2 per million. Those readings now carry the FOLIO environment's estimate in place of the extrapolated figures.

### 6. Private connectivity

Works from the delta VPC through a new interface endpoint to Elastic's serverless endpoint service, a private hosted zone for `private.eu-west-1.aws.elastic.cloud` with a wildcard CNAME to the endpoint, and a private connection policy on the project: 5.6 ms per request. Before any policy is attached the private endpoint answers alongside the public one. Once one is attached, every unmatched source gets 403, which includes the Lambda's public path through NAT, the laptop, and Terraform on the laptop (the provider reports the 403 as "not found"). Operator access from outside the VPC therefore needs an IP policy alongside PrivateLink; the hosted cluster has a public-internet traffic filter today, which is why laptops reach it.

## Change list for #6534, confirmed and amended

- `infrastructure/critical`: a module like the spike's `es_serverless_project`, emitting the nine-key `es_cluster` object with port 443, the private endpoint as `private_host`, and null `read_only` fields. No bootstrap key.
- `elastic_indices`: optional `read_only_username` and `read_only_password`, and the connection secrets map built with an unmarked test so `for_each` accepts it. Already on the branch.
- Networking: interface endpoint, security group and private hosted zone in each VPC that needs the project, a `vpce` policy, and an `ip` policy for operator addresses. The spike creates these in its own root because platform-infrastructure pins ec 0.5.0; the go decision should pick between bumping that provider or keeping them in the pipeline repo's critical root.
- Search power: 28 for the FOLIO environment; revisit from the usage page.
- Code: the `clusterhealth` route in the search API, `reindex_index.py` and `get_reindex_status.py` moving from basic auth to a key with `monitor`, retiring `create_pipeline_storage_users.py`, and the `e_port` fallback in the merger's `application.conf`. The 9.x Python client bump can wait.
- Worth doing for the merger regardless: raise the REST client's connections per route to match its in-flight batch.

## Way forward for production

The production question is whether a serverless project can carry the public API's query load at an acceptable cost and latency, and nothing in this spike measured search under load. I think the route is a second, bounded spike once the FOLIO environment exists, in this order:

1. Hold production-shaped data in a project: works-indexed, images-indexed and concepts-indexed at full size, loaded by the real ingestors from an existing graph date, with the images index's dense vectors in place so the project's general-purpose profile is tested against them.
2. Put the public API's real query mix on it without serving users from it. The catalogue API's multi-cluster mechanism already supports a non-default alias, so the production API can carry a `serverless` entry with the default cluster untouched. catalogue-api's `search_profiling` and `load_test` tools drive the API's search templates at a chosen rate with varied terms; replaying the real production query mix against the alias would need the API's access logs as their input. Measure latency percentiles against the API's own alarms (p95 at ten seconds) and the works and images query times the hosted cluster reports today.
3. Read the search VCU-hours and the search-above-baseline line from the usage page for that week at Performant, then at the On-demand floor, and put both into the estimate in platform-infrastructure#560 in place of the extrapolated line.
4. Decide on cost and the latency figures together. If Performant holds the query mix at today's latencies, the comparison is the measured monthly figure against the hosted cluster's; if it needs High availability, the same reading at that setting.

Two things from this spike go into that one regardless: an operator IP policy next to PrivateLink, since any attached policy locks Terraform and the scripts out otherwise, and the merger's connection pool raised to its in-flight batch, since single-document writers are the stages the write floor penalises most. Elastic support is also worth a question on what search power the console's three tiers set, since this project never showed the labels.

## Open items

- The sliced point-in-time read of works-denormalised-2026-09-30 on the hosted cluster returned 3,096,696, 2,871,430 and 2,977,706 documents in three runs against 3,057,028 in the index, with each slice's count taking one of two values. The index has one primary shard. `core/source.py` reads the same index the same way with 30 slices; a counted read there is worth doing before trusting a single extractor pass.
- The spike project's usage for the whole of 2026-10-15, re-read from the billing API to confirm the idle figure is not understated by feed lag, and a month-by-month export of the semantic prototype's serverless lines.
- Destroy the spike stack through review-gate once those are read.
