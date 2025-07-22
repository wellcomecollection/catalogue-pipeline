# Table of contents

This documentation provides an overview of the Wellcome Collection catalogue pipeline, which creates and maintains the search index for our unified collections search. The pipeline fetches records from multiple source systems, transforms them into a common model, and populates an Elasticsearch index accessible via the catalogue API.

## Core Documentation

* [Catalogue Pipeline](README.md) - Main project overview and high-level design
* [Documentation](docs/README.md) - Introduction to the pipeline and its purpose
  * [Sierra IDs](docs/sierra/sierra_ids.md) - Understanding Sierra library system identifiers
  * [Fetching records from Sierra](docs/adapters/fetching_records_from_sierra.md) - How we retrieve library records
  * [Pipeline](docs/pipeline/README.md) - The works transformation pipeline overview
  * [Developers Guide](docs/developers.md) - Information for developers working on the pipeline
  * [APM](docs/apm.md) - Application Performance Monitoring guidance

## Source Adapters

* [Sierra Adapter](sierra_adapter/README.md) - Library management system records adapter*
*  [METS Adapter](mets_adapter/README.md) - Digitised materials metadata adapter
  * [METS adapter population tool](mets_adapter/populate_mets.md) - Tool for populating METS records
* [CALM Adapter](calm_adapter/README.md) - Archive catalogue records adapter
* [EBSCO Adapter](ebsco_adapter/README.md) - E-journals MARCXML data adapter
* [TEI Adapter](tei_adapter/README.md) - TEI XML files adapter for encoded texts

## Pipeline Components

* [Pipeline](pipeline/README.md) - The works pipeline and transformation logic
* [Catalogue Graph](catalogue_graph/README.md) - Graph pipeline for building knowledge connections
* [Scripts](scripts/README.md) - Utility scripts for pipeline operations and maintenance

## Infrastructure

* [Infrastructure](infrastructure/critical/README.md) - Critical infrastructure configuration and setup
* [Index Configuration](index_config/README.md) - Elasticsearch index mappings and settings

