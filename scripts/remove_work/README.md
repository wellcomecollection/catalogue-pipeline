# Work remover

This script:

- replaces works with InvisibleIdentifiedWorks in ES indices (thereby preventing further reindexing)
- suppresses Miro works in the VHS
- removes images from Loris's S3 buckets
- creates CloudFront invalidations for Loris
- updates the Miro VHS inventory

You also need to create a CloudFront invalidation for `works/<id>` in the wellcomecollection.org distribution.

### Usage

```
Usage: run.py [OPTIONS] CATALOGUE_ID

Options:
  -i, --index TEXT  [required]
```

Because we can no longer get the current ES index from task definitions (it's hardcoded in the `elasticsearch` module), you must specify the index which you want the work removed from.
Multiple indices can be specified like `-i index_1 -i index_2` etc.
