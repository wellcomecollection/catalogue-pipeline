# Introduction

**The catalogue pipeline populates the search index for** [**our online catalogue search**](https://wellcomecollection.org/collections)**.**

This includes:

* Fetching records from our source catalogues, and keeping them up-to-date
* Transforming records from different source catalogues into a single, common model
* Combining records from multiple source catalogues, where appropriate
* Indexing those records into an Elasticsearch index, where they can be fetched by [our catalogue API](https://github.com/wellcomecollection/catalogue-api)

### Documentation <a href="#documentation" id="documentation"></a>

This GitBook space is meant for staff at Wellcome Collection to understand the principles of the catalogue pipeline, including design decisions and user stories.

It does **not** contain information about the operational running of the pipeline, which is liable to go out-of-date quickly (e.g. how to deploy services).

### Repo <a href="#repo" id="repo"></a>

The catalogue pipeline code is in [https://github.com/wellcomecollection/catalogue-pipeline](https://github.com/wellcomecollection/catalogue-pipeline)
