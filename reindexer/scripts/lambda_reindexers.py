import json


def invoke_lambda_reindexers(session):
    """
    Invoke all Lambda re-indexers.

    The Lambda re-indexers are invoked asynchronously, so this function
    will return immediately after invoking them.
    """

    # There is currently only one Lambda re-indexer, the intention is
    # to add more in the future allowing the adapter to hold the logic
    # for performing re-indexes.
    function_names = ["ebsco-adapter-ftp"]
    payload = {
        "reindex_type": "full"
    }

    lambda_client = session.client("lambda")

    for function_name in function_names:
        lambda_client.invoke(
            FunctionName=function_name,
            InvocationType='Event',
            Payload=json.dumps(payload).encode('utf-8')
        )
