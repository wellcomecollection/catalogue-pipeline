# pipeline_storage

This stack contains the pipeline_storage cluster we use for intermediate storage in the pipeline.

Why does this live in its own stack?

*   It's not in `infrastructure/critical` because it's an ephemeral resource.
    It'd be annoying if we lost it, but we can recreate all the data it contains.

    We keep it separate from more important resources -- say, the ID minter database -- to reduce the risk of a bad Terraform change hosing something we really care about.

*   It's not in `pipeline` because it has to be managed using the `run_terraform.sh` script to get Elastic Cloud credentials.

    This is moderately inconvenient -- you can't use it by running vanilla Terraform.
    For now I don't want to make the pipeline stack harder to use.

## Scaling up the cluster during reindexes

We can't afford to run a high-capacity cluster all the time, but we also don't need to.
The cluster is under heavy load when we're reindexing the catalogue pipeline, but that's it.

*   When the pipeline is idle, we leave it at a single 8GB node.
*   When we're reindexing, it's helpful to turn it up to 29GB and add an extra zone.

Remember to turn it down again afterwards!

## Running terraform

In order to run terraform the Elastic Cloud terraform provider requires that the EC_API_KEY environment variable be set.

You can run `run_terraform.sh` to set the correct environment variable and run terraform. Any parameters you pass to that script will be passed to `terraform`.

The script will by default set AWS_PROFILE to "platform", but you can use a different value by setting AWS_PROFILE yourself.
