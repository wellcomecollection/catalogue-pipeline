tweak

# Catalogue

| CI Pipeline       | Status                                                                                                                                                                    |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Build             | [![Build status](https://badge.buildkite.com/0ca819db1215b66ecb17019d8ee5331d8e537094d051141219.svg?branch=master)](https://buildkite.com/wellcomecollection/catalogue)   |
| Integration tests | [![Build status](https://badge.buildkite.com/31a06ac64ab4f09ca5bc5930e21a57889c3f02561260f18ae6.svg?branch=main)](https://buildkite.com/wellcomecollection/integration) |

## Purpose

Making Wellcome Collection's catalogue open, accessible and
discoverable.

The catalogue consists of multiple sources including:
* Library holdings
* Archives and manuscripts
* Born digital content
* Images from what was previously wellcomeimages.org

As and when these sources are made available digitally, we will consume
them via [our pipeline](./pipeline), unify them into a
[single model](./common/internal_model) and make them discoverable via
our [API](./api).

**Interested in how we make these services?**

[Take a look at our documentation on the design and decision making
processes of the services within the catalogue repo][catalogue docs].

**Interested in making use of our data to build your own products or
use in your research?**

[Take look at our developer documentation][api developer docs] or
[go straight to our API][api].

**Interested in other parts of the Wellome Collection digital platform
works?**

[Take a look at our Platform repo][platform repo]

**Interested in how all of this works**
[Keep reading about the architecture of the services in this repo](#architecture).


## Architecture

The catalogue consists of three main parts with supporting services.
These are:

* [Sierra adapter](sierra_adapter.md): Allowing us to store data from
  Sierra that is retrievable performantly and at scale.
* [Pipeline](pipeline.md): Taking adapter data and putting it into our query index, Elasticsearch
* [API](api/): Serving the data from Elastic search to clients via HTTP


## Dependencies

* Java 1.8
* Scala 2.12
* SBT
* Terraform 0.11
* Docker
* Make


## Problems you might have

* **Stack overflow from scalac \(in IntelliJ\) when building projects**:

  Go to `Settings > Build, Execution, Deployment > Compiler` and change
  `Build process heap size (Mbytes)` to something large, eg 2048.

* **Pulling docker containers from ECR**

  You'll need to log into ECR before local docker can pull from there:
  ```bash
  # bash etc
  eval $(AWS_PROFILE=platform-developer aws ecr get-login --no-include-email)

  # fish
  eval (env AWS_PROFILE=platform-dev aws ecr get-login --no-include-email)
  ```

## Deploying

We deploy ECS catalogue services using the [weco-deploy](https://github.com/wellcomecollection/weco-deploy) tool.

The [current latest default branch](https://buildkite.com/wellcomecollection/catalogue) build deploys to staging automatically.

### Deploying to production

After automated deployment to the staging environment, we run [integration tests](https://buildkite.com/wellcomecollection/integration) against the staging API and front-end.

**When deploying a release from staging to production you should check these tests pass.**


## Things you might want to do

Generally small things you might want to do irregularly involving the
API & data are stored within \[`./scripts`\].

---

Part of the [Wellcome Digital Platform][platform repo].


[catalogue docs]: https://docs.wellcomecollection.org/catalogue/
[api developer docs]: https://developers.wellcomecollection.org/catalogue/
[api]: https://api.wellcomecollection.org/catalogue
[platform repo]: [https://github.com/wellcomecollection/platform]
