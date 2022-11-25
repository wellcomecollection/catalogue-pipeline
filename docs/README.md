# Introduction

**The catalogue pipeline populates the search index for [our online catalogue search](https://wellcomecollection.org/collections).**

This includes:

* fetching records from source catalogues and keeping them up-to-date
* transforming records into a single, common model
* combining records from multiple sources, where appropriate
* creating an Elasticsearch index which can be queried by [the catalogue API](https://github.com/wellcomecollection/catalogue-api)

## Documentation

This GitBook space is meant to provide a high-level overview of the catalogue pipeline and its design.

It does **not** contain specific operational details, e.g. how to deploy specific services. This is kept inside the code repository.

## Repo

The catalogue pipeline code is in <https://github.com/wellcomecollection/catalogue-pipeline>
