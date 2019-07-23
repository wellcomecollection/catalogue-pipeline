# Catalogue pipeline

All data comes from an external data source, e.g. [Sierra and it's adapter](../sierra_adapter),
onto a queue for entering the pipeline, and finally makes it into out query index,
[Elasticsearch](https://www.elastic.co/products/elasticsearch).

The flow of data is as follows:
```
(Data source queue) => Transformer => Recorder => ID Minter => Matcher => Merger => Ingestor
```
Each service in the pipeline has an input of an SNS topic that it subscribes to and after it has worked on that message, pushes its result to a SQS queue.

We use AWS SNS / SQS for this, there are talks of abstracting that out.  

```
SNS => Service => SQS
``` 

## [Transformer](./transformer)

Each data source will have their own transformer e.g. 
* [Miro](./transformer/transformer_miro)
* [Sierra](./transformer/transformer_sierra)

These take the original source data from an adapter, and transform them into a Work (Unidentified).


## [Recorder](./recorder)

Each transformed work is stored into a
[VHS store](https://stacks.wellcomecollection.org/creating-a-data-store-from-s3-and-dynamodb-8bb9ecce8fc1).


## [ID Minter](./id_minter)

Each Unidentified Work has an ID minted for it, using a source ID and avoiding dupes. 


## [Matcher](./matcher)

Searches for potential merge candidates, and records them on the Work. 


## [Merger](./merger)

Runs some [rules](./merger/src/test/scala/uk/ac/wellcome/platform/merger/rules) on the merge candidates
and decides if it is a valid merge.


## [Ingestor](./ingestor)

Inserts a work into our query index - Elasticsearch.
