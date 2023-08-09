# Reindexing

A reindex operation runs the source data from the [adapters](docs/adapters/README.md) through the pipeline causing it to be re-transformed / matched & merged as appropriate.

## How to run a reindex

To run a reindex follow these steps:

1. [Terraform a new pipeline](#terraform-a-new-pipeline)
2. [Run the reindex script](#run-the-reindex-script)

### Terraform a new pipeline

Copy one of the per-pipeline folders in `pipeline/terraform` – these are labelled with the date of the pipeline.
Rename the new folder with the date of your pipeline (usually the current date).

Update the `reindexing_state` variables in `main.tf` – you want them all to be `true` if you're about to do a complete reindex, as this adds extra capacity and scaling to the pipeline.

NOTE: once the reindexing of the new pipeline has completed, change `true` to `false` then `terraform apply` the changes to scale ES clusters/services down.
⚠️ This can only be performed once a day so time it right!

Remember to create a pull request with this change.

You can now run `terraform` inside the folder you've created:

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

