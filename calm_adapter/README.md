# CALM adapter

CALM is our archive catalogue.
The CALM adapter fetches new records from CALM and keeps our copy of the CALM database up-to-date.

## Key services/libraries

*   The `calm_api_client` library is a generic client for the CALM API.
    It's a SOAP API that allows you to query CALM records.

    There's a [CALM API guide][api_guide] in one of our private S3 buckets.

    [api_guide]: https://us-east-1.console.aws.amazon.com/s3/object/wellcomecollection-platform-infra?prefix=Calm.API.Guide.pdf&region=eu-west-1

*   The `calm_adapter` service fetches updated records from CALM.

    It receives queries from the `calm_window_generator`, which tell it what sort of records to fetch.
    e.g.

    ```json
    {
      "start": "2001-01-01T01:00:00",
      "end":   "2002-02-02T02:00:00",
      "type": "CreatedOrModifiedDate"
    }
    ```

    This query tells the `calm_adapter` service to fetch any records which were updated between 1 Jan 2001 and 2 Feb 2002.

    The window generator can query based on created date, modified date, or the RefNo if you want to get updates for specific records.

    The window generator runs as a Lambda on a fixed schedule, or it can be run locally if you want to do a one-off query.
    e.g. you can refetch all the records modified on a given day.

    TODO: Should we rename this to "query generator"?

    NOTE: The CALM adapter uses a different format to the TEI/Sierra adapters, which is why it doesn't use the shared window generator.

*   When records are deleted from CALM, they disappear immediately.
    They no longer appear in the API.

    Because we want to spot when records are deleted, we have the `calm_deletion_checker` that polls CALM to look for deleted records (by looking for every record we know about, and checking if it's still in the API).
    It's triggered by the `calm_deletion_check_initiator`.

*   The `calm_indexer` service indexes CALM records in the reporting cluster.
    This is meant for ad hoc analysis of the CALM data, e.g. when designing a new transformation rule.
