# catalogue_api

The stack containing the Catalogue API

## Overview

Contains the Catalogue API, and attendant ECS Script Tasks.


## Deployment

### Steps

* open [`terraform/locals.tf`](terraform/locals.tf)
* there are two versions of the API deployed, `romulus` and `remus`
* `api.wellcomecollection.org` will be pointing to the deployment referenced in `production_api`. `stage-api.` will
  be pointing to the other.
* change the ECS container reference to your new version on the staging deployment
* `terraform apply`
* test on `api-stage.`
* once satisfied, change `production_api` to the new deployment
