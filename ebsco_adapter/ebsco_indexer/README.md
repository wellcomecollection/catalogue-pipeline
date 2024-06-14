# EBSCO indexer

This includes source code for the `ebsco-adapter-indexer` Lambda function, which indexes EBSCO items into the 
Elasticsearch reporting cluster (https://reporting.wellcomecollection.org). 

The indexer Lambda is triggered when the adapter Lambda pushes a new message to SNS, indicating that a new EBSCO item
needs to be indexed/deleted.

## Running the indexer locally

To manually index documents from a local environment, navigate to the `ebsco_indexer` directory and run:  
```sh
AWS_PROFILE=platform-developer \
ES_INDEX=ebsco-index \
python3 src/main.py --ebsco-id <EBSCO_ITEM_ID> --s3-bucket <BUCKET_CONTAINING_EBSCO_XML> --s3-key <KEY_CONTAINING_EBSCO_XML>
```

To delete a document from the index, run:
```sh
AWS_PROFILE=platform-developer \
ES_INDEX=ebsco-index \
python3 src/main.py --ebsco-id <EBSCO_ITEM_ID> --s3-bucket <BUCKET_CONTAINING_EBSCO_XML> --s3-key <KEY_CONTAINING_EBSCO_XML> --delete true
```

## Recreating the index

To recreate the `ebsco_fields` index with new mappings, see the `index_utils.py` file.
Note that running the code in this file will remove all indexed documents from the index.

