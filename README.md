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
our [API](https://github.com/wellcomecollection/catalogue-api).

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

* Adapters: Syncing data from multiple external sources, enabling retrieving data performantly and at scale:
  - [Sierra adapter](sierra_adapter/README.md): [Sierra](https://www.iii.com/products/sierra-ils/) contains data on things in the library
  - [Calm adapter](calm_adapter/README.md): [Calm](https://www.axiell.com/uk/solutions/product/calm/) contains data on things in the archive
  - [METS adapter](mets_adapter/README.md): [METS](http://www.loc.gov/standards/mets/) data on digital assets from our workflow & archival storage systems. 
* [Pipeline](pipeline.md): Taking adapter data and putting it into our query index. We use [Elasticsearch](https://www.elastic.co/elasticsearch/) as our underlying search engine.
* [API](https://github.com/wellcomecollection/catalogue-api/blob/main/README.md): The public APIs to query our catalogue data. The API services are stored in a different GitHub repository: https://github.com/wellcomecollection/catalogue-api


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
  aws ecr get-login-password --region eu-west-1 --profile platform-dev | \
  docker login --username AWS --password-stdin 760097843905.dkr.ecr.eu-west-1.amazonaws.com
  ```


## Deploying

We deploy ECS catalogue services using the [weco-deploy](https://github.com/wellcomecollection/weco-deploy) tool.


## Things you might want to do

Generally small things you might want to do irregularly involving the
API & data are stored within \[`./scripts`\].

---

Part of the [Wellcome Digital Platform][platform repo].

[catalogue docs]: https://docs.wellcomecollection.org/catalogue/
[api developer docs]: https://developers.wellcomecollection.org/catalogue/
[api]: https://api.wellcomecollection.org/catalogue
[platform repo]: [https://github.com/wellcomecollection/platform]
