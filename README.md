# catalogue-pipeline

[![Build status](https://badge.buildkite.com/0ca819db1215b66ecb17019d8ee5331d8e537094d051141219.svg?branch=main)](https://buildkite.com/wellcomecollection/catalogue-pipeline) [![Adapter deployment status](https://img.shields.io/buildkite/2fb18a042947b93fb2b05a8c7b48c5db0e7fd0f9210bb993d5/main.svg?label=adapter%20deployment)](https://buildkite.com/wellcomecollection/catalogue-pipeline-deploy-adapters) [![Pipeline deployment status](https://img.shields.io/buildkite/120d56989228052f1539823186545fd7e1665aaa2cb98d0c91/main.svg?label=pipeline%20deployment)](https://buildkite.com/wellcomecollection/catalogue-pipeline-deploy-pipeline)

The catalogue pipeline creates the search index for our [unified collections search][search].
It populates an Elasticsearch index with data which can then be read by our [catalogue API][api].
This allows users to search data from all our catalogues in one place, rather than searching multiple systems which each have different views of the data.

[search]: https://wellcomecollection.org/works
[api]: https://github.com/wellcomecollection/catalogue-api



## Requirements

The catalogue pipeline is designed to:

*   Create a single search index for records from all our source systems (including image collections, library catalogue, and archive records)
*   Stay up-to-date with updates and changes in those source systems
*   Transform those records into a common model
*   Combine records from different systems that refer to the same object



## High-level design

<img src="docs/images/high_level_design.svg">

We have a series of "adapters" that fetch records from our source systems.
The adapters are responsible for staying up-to-date with changes in the source systems.

The adapters feed a transformation pipeline, which transforms source records into a common model, adds a pipeline identifier, and combines records from different systems.
The structure and logic of the transformation pipeline evolves over time, as we find new and better ways to transform the data.

Once the transformation pipeline has finished processing the records, it stores them in a search index, which can be read by the [catalogue API][api].

The catalogue pipeline runs entirely in AWS, with no on-premise infrastructure required.  

See [here](./pipeline/README.md) for more details.



## Usage

We always have at least one pipeline which is populating the currently-live search index, but we may have more than one pipeline running at a time.

Running multiple pipelines means we can try experiments or breaking changes in a new pipeline, and keep them isolated from the live search index (and the public API).
Over time, newer pipelines replace older pipelines, and the older pipelines are deleted.

We publish our source code so that other people can learn from it, but it's very unlikely anybody would want to run it themselves.
It contains a lot of Wellcome-specific logic, and would need extensive modification to be useful elsewhere.



## Development

See [docs/developers.md](docs/developers.md).



## License

MIT.
