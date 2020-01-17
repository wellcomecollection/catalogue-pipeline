# Catalogue

[![Build Status](https://travis-ci.org/wellcometrust/catalogue.svg?branch=master)](https://travis-ci.org/wellcometrust/catalogue)


## Purpose

Making Wellcome Collection's catalogue data and information open and discoverable
via public APIs.

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


## Things you might want to do

Generally small things you might want to do irregularly involving the
API & data are stored within \[`./scripts`\].

---

Part of the [Wellcome Digital Platform][platform repo].


[catalogue docs]: https://docs.wellcomecollection.org/catalogue/
[api developer docs]: https://developers.wellcomecollection.org/catalogue/
[api]: https://api.wellcomecollection.org/catalogue
[platform repo]: [https://github.com/wellcometrust/platform]
