# Testing the inference manager

Fully testing these services is, unfortunately, a pain - our testing dependencies make it hard to write integration tests that can be run easily in a local environment but that also play well with CI and - in particular - SBT.

## Running the non-integration tests

If you don't want to run the integration tests and are happy to run the other scala tests from (eg) your IDE, then things are quite straightforward:

1. Start the SQS service _only_: `docker-compose up -d sqs`
2. Run your tests! Note that `ManagerInferrerIntegrationTest` will stall and eventually fail.

## Running the integration tests

In general, you shouldn't need to iterate on the integration tests, and instead should treat them as an additional layer of security provided by the CI server. Local testing of the individual services should suffice for iteration and documentation of the inter-service contracts. However, if you do need to run the tests locally, this is how:

The `docker-compose.yml` has 2 oddities that make running the tests locally really quite difficult - these are necessary for it to work with CI and the docker-compose SBT plugin that we use.

- You need to provide a `ROOT` environment variable providing the _absolute_ path of the repo root, eg:
  ```bash
  ROOT=/Users/me/repos/catalogue docker-compose up -d
  ```
  This is necessary in order for the inferrer services to share the data bind mount.
- The inferrers need to be built/tagged as per the `image` fields in the compose file ahead of time - they can't use a `build` key with a path (this is a limitation of the SBT plugin).

Other than manually building/tagging to fulfil the latter requirement, you can also just run all the tests with `make inference_manager-test` or, for the integration test only, `make inference_manager-integration-only`.

These will build and tag the inferrer services appropriately, and once this has happened you can run the tests and `docker-compose up` as you wish (in fact you don't need to wait for them to run to do this, just for the builds to have happened).

In order to iterate on the inferrers while running the integration test you will need to keep re-building and re-upping the containers as there are no live code volumes (or temporarily modify the docker-compose file to suit): `docker build ../foo_inferrer -t foo_inferrer`.
