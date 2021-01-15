# Catalogue Critical Infrastructure

Contains critical infrastructure for the catalogue services

## Running terraform

In order to run terraform the Elastic Cloud terraform provider requires that the EC_API_KEY environment variable be set.

You can run `run_terraform.sh` to set the correct environment variable and run terraform. Any parameters you pass to that script will be passed to `terraform`.

The script will by default set AWS_PROFILE to "platform", but you can use a different value by setting AWS_PROFILE yourself.
