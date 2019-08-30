# Catalogue pipeline

## Structure

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

### [Transformer](./transformer)

Each data source will have their own transformer e.g. 
* [Miro](./transformer/transformer_miro)
* [Sierra](./transformer/transformer_sierra)

These take the original source data from an adapter, and transform them into a Work (Unidentified).


### [Recorder](./recorder)

Each transformed work is stored into a
[VHS store](https://stacks.wellcomecollection.org/creating-a-data-store-from-s3-and-dynamodb-8bb9ecce8fc1).


### [Matcher](./matcher)

Searches for potential merge candidates, and records them on the Work. 


### [Merger](./merger)

Runs some [rules](./merger/src/test/scala/uk/ac/wellcome/platform/merger/rules) on the merge candidates
and decides if it is a valid merge.


### [ID Minter](./id_minter)

Each Unidentified Work has an ID minted for it, using a source ID and avoiding dupes. 


### [Ingestor](./ingestor)

Inserts a work into our query index - Elasticsearch.


## Releasing

1. Create a new release with the [Wellcome Release Tool](https://github.com/wellcometrust/dockerfiles/tree/master/release_tooling)
  to `stage` 
      ```BASH
      wrt prepare
      wrt deploy stage
      ```
2. Create a new stack by copying the current one in
  [`./terraform/main.tf`](`./terraform/main.tf`).
3. Change the namespace and index name
4. Make sure the reindex topics are uncommented. You probably only need the Sierra one.
      ```HCL
      "${local.sierra_reindexer_topic_name}"
      "${local.miro_reindexer_topic_name}"
      ```
5. Run
      ```BASH
      terraform init
      terraform apply
      ```
6. Run the [`reindexer`](../reindexer)
    ```
    python3 ./start_reindex.py --src sierra --dst catalogue --reason "Great Good" --mode partial
    ```
7. Watch your new pipeline do it's magic and land up as expected in the new index 🔮

### If there were no model changes
1. Change the name of the index to the current index
2. Comment out the reindex topics
3. Remove the old stack
4. Remove the test index
5. ```terraform apply```

### If there were model changes
1. Do a complete reindex into the new index
2. Once you're happy it's up to date, remove the old stack
3. Change the index that the API is pointing to on the staging API
4. Test
5. Make the stage API the prod api
