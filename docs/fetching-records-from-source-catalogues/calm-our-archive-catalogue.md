# CALM: Our archive catalogue

CALM is the database used by Collections Information to manage our archives catalogue.

## Identifiers in CALM

A record in CALM has three identifiers:

*   A **RecordID**, which is a UUID used by the CALM database, e.g. `002c5acf-a977-4f1e-ae6f-bcf84143ec05`

*   A **RefNo**, which is a slash-separated string that tells us the position of a record in an archive hierachy, e.g. `PPMLV/C/7/6/6`.

    All the records in the same archive will have the same prefix before the first slash, e.g. everything with a RefNo that starts `PPMLV/` is part of the Dr Marthe Louise Vogt archive.

    PP is a common prefix that stands for "Personal Papers"; SA is another that stands for "Societies and Associations".

*   An **AltRefNo**, which is the display version of the RefNo, e.g. `PP/MLV/C/7/6/6`.

    This may be formatted slightly differently, and should not be analysed as a structured string.

## The CALM API

There's no publicly available documentation, but there's a [CALM API guide][api_guide] in one of our private S3 buckets.

[api_guide]: https://us-east-1.console.aws.amazon.com/s3/object/wellcomecollection-platform-infra?prefix=Calm.API.Guide.pdf&region=eu-west-1

## How we get updates

*   We poll CALM on a fixed interval, and retrieve any records which have been created or modified since the last poll.

*   We don't pull complete CALM records into the pipeline; we suppress a handful of fields, e.g. those which contain personally identifiable information (PII).
    We'd never present that in the catalogue API, and creating another copy is unnecessary.
    See [suppressed fields in CALM](https://github.com/wellcomecollection/private/blob/main/2020-04-calm-suppressed-fields.md) for more detail.

*   When records are deleted from CALM, they disappear immediately.
    They no longer appear in the API.

    We have a separate app that looks for deleted records, by comparing the records CALM knows about and the records we know about.
    e.g. if CALM thinks there are 9 records and we think there are 10, we know that we need to remove 1 record from the catalogue pipeline.
