# EBSCO indexer

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
