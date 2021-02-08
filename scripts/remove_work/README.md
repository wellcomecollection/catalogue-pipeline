# Work remover

This script:

- replaces works with InvisibleIdentifiedWorks in ES indices (thereby preventing further reindexing)
- (optionally) removes associated images from the image indices
- suppresses Miro works in the VHS
- removes images from Loris's S3 buckets
- creates CloudFront invalidations for Loris and wellcomecollection.org
- updates the Miro VHS inventory

### Usage

```
Usage: run.py [OPTIONS] CATALOGUE_ID

Options:
  -i, --index TEXT  [required]
```

Because we can no longer get the current ES index from task definitions (it's hardcoded in the `elasticsearch` module), you must specify the index which you want the work removed from.
Multiple indices can be specified like `-i index_1 -i index_2` etc.
