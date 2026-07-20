# Deployment

This page describes how catalogue pipeline code gets from a merged pull request to the services that run it, and how that works when more than one pipeline is active.

## Dated pipelines

A pipeline is a dated, self-contained processing stack. Each directory named `YYYY-MM-DD` under `pipeline/terraform/` is one pipeline: a Terraform root module with its own state file, its own ECS cluster (`catalogue-<date>`), its own lambdas (`catalogue-<date>-*`), and its own Elasticsearch cluster and indexes. There is no central registry of pipelines; the set of dated directories *is* the set of pipelines.

We always have at least one pipeline populating the live index, and we sometimes run more than one, for example while reindexing into a new pipeline before switching the API over to it. See [REINDEXING.md](../../REINDEXING.md) for the lifecycle of creating a new pipeline and promoting it to production.

## Image tags

All container images live in ECR under `760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/<repository>`. Three kinds of tag matter (see `builds/update_ecr_image_tag.sh`):

* a commit tag (the git SHA), which is immutable and ties an image to its source;
* `latest`, the most recently published image in a repository;
* floating environment tags, which are what running infrastructure actually references. Dated pipeline services reference `env.<date>` (e.g. `env.2025-10-02`); shared services such as adapters reference `env.prod` or `prod`.

Terraform bakes the `env.<date>` reference into ECS task definitions and lambda configuration once. Routine deployments never run Terraform: they move the floating tag to a new image and then tell ECS or Lambda to redeploy.

Because each pipeline has its own `env.<date>` tag, different pipelines can run different versions of the applications.

## The two deployment paths

Code is built and deployed by two CI systems.

**Buildkite** builds the Scala applications (transformers, matcher, merger, inferrers, and the source adapters). On a merge to main it publishes each image with its commit tag and `latest`, then triggers two deploy pipelines:

* `catalogue-pipeline-deploy-pipeline` runs `.buildkite/scripts/deploy_latest_pipeline.py`, which chooses the target pipelines (see below) and for each one runs `builds/deploy_catalogue_pipeline.sh`: retag `latest` as `env.<date>`, force a new deployment of the ECS services in the `catalogue-<date>` cluster, and update the `catalogue-<date>-*` lambdas.
* `catalogue-pipeline-deploy-adapters` deploys the shared adapter services (Sierra, Calm, METS, TEI) by moving `env.prod` and redeploying their fixed clusters. Adapters are singletons: they are deployed once and feed every active pipeline.

**GitHub Actions** builds the Python "unified pipeline" images (`unified_pipeline_task` and `unified_pipeline_lambda`) from `catalogue_graph/`. On a merge to main, `catalogue-graph-deploy.yml`:

* retags the images as `prod` and updates the shared adapter trigger lambdas (EBSCO, Axiell, FOLIO);
* retags the images as `env.<date>` for each target pipeline and updates that pipeline's dated lambdas (id minter, transformer, graph components, image inferrer).

## Which pipelines get deployed

By default, both CI systems deploy to a single pipeline: the one with the lexicographically greatest date under `pipeline/terraform/`. Older pipelines keep running whatever their `env.<date>` tag last pointed at.

`pipeline/terraform/deploy_settings.json` changes this behaviour:

```json
{
  "deploy_all_pipelines": false
}
```

When `deploy_all_pipelines` is `true`, every dated pipeline directory is deployed on each merge to main. Buildkite reads the file in `deploy_latest_pipeline.py`; GitHub Actions reads it in the `discover-pipeline-dates` job (`.github/actions/discover-pipeline-dates`). A failure deploying one pipeline does not stop the others, but the build fails at the end if any pipeline failed.

Turn the flag on when several pipelines should all track main, for example during an extended migration where an old and a new pipeline run side by side. Leave it off (the default) whenever an older pipeline is deliberately pinned, for example when a code change is incompatible with an older pipeline's index mappings. Flipping the flag is a pull request against `deploy_settings.json`.

To deploy an older pipeline manually while the flag is off, run:

```console
PIPELINE_DATE="2025-10-02" builds/deploy_catalogue_pipeline.sh tag_images_and_deploy_services
```

Note that this deploys whatever `latest` currently points at, not a specific commit.

The Buildkite deploy script also looks up the works index currently served by the catalogue API (`/_elasticConfig`) and warns when the newest pipeline is not the production pipeline. Which pipeline is "production" is not recorded anywhere in this repo: it is whichever pipeline's index the [catalogue-api](https://github.com/wellcomecollection/catalogue-api) is configured to serve.
