# Reindexing

A reindex operation runs the source data from the [adapters](docs/adapters/README.md) through the pipeline causing it to be re-transformed / matched & merged as appropriate.

## How to run a reindex

To run a reindex follow these steps:

1. [Prepare a release](#prepare-a-release)
2. [Deploy a release](#deploy-a-release)
3. [Terraform a new pipeline](#terraform-a-new-pipeline)
4. [Run the reindex script](#run-the-reindex-script)

### Prepare a release

Prepare a release following the example below.

You may want to choose a specific git ref as a label and _not_ `latest`.

```
> weco-deploy \
  --project-id catalogue_pipeline prepare \
  --from-label latest \
  --description "A helpful description"

Prepared release from images in latest
Requested by: dev@Wellcomecloud.onmicrosoft.com
Date created: 2021-07-08T15:11:48.403652

service                old image    new image    Git commit
---------------------  -----------  -----------  ------------
aspect_ratio_inferrer  efd451f      -            -
...

Created release eeb9482f-0a6a-4211-8de9-27cee7567134
```

### Deploy a release

You will need to specify:

- `release-id`: Gathered from preparing the release with `weco-deploy`
- `environment-id`: The environment identifier added to [`.wellcome-project`](.wellcome_project)

Using this you can do a `weco-deploy deploy` operation:

```
> weco-deploy --project-id catalogue_pipeline deploy \
  --release-id eeb9482f-0a6a-4211-8de9-27cee7567134 \
  --environment-id YYYY-MM-DD \
  --description "A helpful description"

Deploying release eeb9482f-0a6a-4211-8de9-27cee7567134
Targeting env: YYYY-MM-DD (Environment(id='YYYY-MM-DD', name='YYYY-MM-DD'))
Requested by: dev@Wellcomecloud.onmicrosoft.com
Date created: 2021-07-08T15:11:48.403652

ECS services discovered:

image ID               services
---------------------  ----------
aspect_ratio_inferrer
...

Create deployment? [y/N]: y

Deployment Summary
Requested by: dev@Wellcomecloud.onmicrosoft.com
Date created: 2021-07-08T15:23:55.267305
Deploy data:  deploy_2021-07-08_16-23-55.json

image ID               summary of changes
---------------------  --------------------
aspect_ratio_inferrer  ECR tag updated,
...

Deployed release eeb9482f-0a6a-4211-8de9-27cee7567134 to YYYY-MM-DD (Environment(id='YYYY-MM-DD', name='YYYY-MM-DD'))
Deployment of eeb9482f-0a6a-4211-8de9-27cee7567134 to YYYY-MM-DD successful
```

This operation **only tags ECR images with the new environment** it does not deploy any services.

This will be done when you terraform a new pipeline.

### Terraform a new pipeline

You will now need to create a new pipeline module in [./pipeline/terraform/main.tf](./pipeline/terraform/main.tf).

Copy and paste an existing pipeline making sure to update the fields:

- `pipeline_date`: References secrets required to access ES and to sets internal infrastructure labels.
- `release_label`: Sets the ECR label to use on the service deployment images, created in the above deployment process.
- `is_reindexing`: Sets ES cluster/service scaling limits while reindexing, and connects reindexing topics. Enabling this will incur above normal costs for a pipeline.

See the following example:

```tf
module "catalogue_pipeline_2021-07-06" {
  source = "./stack"

  pipeline_date = "2021-07-06"
  release_label = "2021-07-06"

  is_reindexing = false

  # Boilerplate that shouldn't change between pipelines.
  # ...
}

module "catalogue_pipeline_YYYY-MM-DD" {
  source = "./stack"

  pipeline_date = "YYYY-MM-DD"
  release_label = "YYYY-MM-DD"

  is_reindexing = true

  # Boilerplate that shouldn't change between pipelines.
  # ...
}
```

Remember to create a pull request with this change.

You can now run `terraform` in  [./pipeline/terraform](./pipeline/terraform):

```
# Use the run_terraform.sh script to get Elastic Cloud credentials
> ./run_terraform.sh plan

...

Plan: 389 to add, 0 to change, 0 to destroy.

# Before applying review the plan operation and verify it makes sense
> ./run_terraform.sh apply
```

### Run the reindex script

Now we have our pipeline connected for reindexing and running our chosen version of the pipeline code we can start a reindex operation.

#### Start the reindex

The reindex script can be found in [./reindexer/start_reindex.py](./reindexer/start_reindex.py).

```
> python3 ./start_reindex.py

Which source do you want to reindex? (all, miro, sierra, mets, calm): all
Which pipeline are you sending this to? (catalogue, catalogue_miro_updates, reporting): catalogue
Every record (complete), just a few (partial), or specific records (specific)? (complete, partial, specific): partial
```

A partial reindex will allow sending a few records (by default 10) in order to verify that a pipeline is functioning as expected without incurring the costs of a full reindex.

#### Monitor the reindex

You can monitor a reindex in progress using Grafana at [https://monitoring.wellcomecollection.org/](https://monitoring.wellcomecollection.org/), or by looking at CloudWatch metrics in the `platform` AWS account.

Non-empty DLQs will be reported in the Wellcome #wc-platform-alerts Slack channel.

You can monitor a reindex using the [./reindexer/get_reindex_status.py](./reindexer/get_reindex_status.py) script, specifying the ID of the pipeline you wish to check.

```
> python3 get_reindex_status.py YYYY-MM-DD

*** Source tables ***
...
sierra  2,168,470
TOTAL   3,243,477

*** Work index stats ***
source records      3,243,477
...
works-indexed       3,243,477
API                 3,242,763  ▼ 714

*** Image index stats ***
...
images-indexed    144,981
API               144,981

Approximately 99% of records have been reindexed successfully (counts may not be exact)
```

A reindex should take a few hours to complete.

#### Updating configuration

When you have a complete successful reindex you will want to present it via the Catalogue API.

A new index can be referenced by updating the [`ElasticConfig` object](https://github.com/wellcomecollection/catalogue-api/blob/main/common/display/src/main/scala/weco/catalogue/display_model/ElasticConfig.scala#L15):

The `indexDate` should be the one used to reference the deployment and terraformed pipeline:

```scala
object ElasticConfig {
  // We use this to share config across API applications
  // i.e. The API and the snapshot generator.
  val indexDate = "YYYY-MM-DD"

  def apply(): ElasticConfig =
    ElasticConfig(
      worksIndex = Index(s"works-indexed-$indexDate"),
      imagesIndex = Index(s"images-indexed-$indexDate")
    )
}
```

You will want to PR & deploy this change through the API stage environment and allow CI to perform the usual API checks.

Be sure to check the [diff_tool](https://github.com/wellcomecollection/catalogue-api/tree/main/diff_tool) output in CI in before deploying to production (you can also run this manually).

Visit the [Buildkite job for catalogue-api](https://buildkite.com/wellcomecollection/catalogue-api-deploy-prod/builds?branch=main) job for your PR after it is merged to `main` to view the `diff_tool` output and access the "Deploy to prod" button.

