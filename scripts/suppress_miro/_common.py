import concurrent.futures
import itertools
import re
import subprocess
import base64

import boto3
from elasticsearch import Elasticsearch


def get_session(*, role_arn):
    """
    Returns a boto3 Session authenticated with the current role ARN.
    """
    sts_client = boto3.client("sts")
    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.Session(
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def get_secret_string(session, *, secret_id):
    """
    Look up the value of a SecretString in Secrets Manager.
    """
    secrets = session.client("secretsmanager")
    return secrets.get_secret_value(SecretId=secret_id)["SecretString"]


def _get_pipeline_cluster(session, *, date):
    host = get_secret_string(
        session, secret_id=f"elasticsearch/pipeline_storage_{date}/public_host"
    )
    port = get_secret_string(
        session, secret_id=f"elasticsearch/pipeline_storage_{date}/port"
    )
    protocol = get_secret_string(
        session, secret_id=f"elasticsearch/pipeline_storage_{date}/protocol"
    )
    return host, port, protocol


def get_api_es_client(date):
    """
    Returns an Elasticsearch client with read-only privileges for the catalogue cluster.
    """
    session = get_session(role_arn="arn:aws:iam::756629837203:role/catalogue-developer")
    host, port, protocol = _get_pipeline_cluster(session, date=date)
    api_key = get_secret_string(
        session,
        secret_id=f"elasticsearch/pipeline_storage_{date}/catalogue_api/api_key",
    )
    decoded_api_id_and_key = base64.b64decode(api_key).decode("utf-8").split(":")

    return Elasticsearch(
        f"{protocol}://{host}:{port}",
        api_key=(decoded_api_id_and_key[0], decoded_api_id_and_key[1]),
    )


def get_ingestor_es_client(date, doc_type):
    """
    Returns an Elasticsearch client with write privileges for the catalogue cluster.
    """
    session = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")
    host, port, protocol = _get_pipeline_cluster(session, date=date)
    api_key = get_secret_string(
        session,
        secret_id=f"elasticsearch/pipeline_storage_{date}/{doc_type}_ingestor/api_key",
    )
    decoded_api_id_and_key = base64.b64decode(api_key).decode("utf-8").split(":")

    return Elasticsearch(
        f"{protocol}://{host}:{port}",
        api_key=(decoded_api_id_and_key[0], decoded_api_id_and_key[1]),
    )


def get_date_from_index_name(index_name):
    date_match = re.search(r"-(\d{4}-\d{2}-\d{2})$", index_name)
    if not date_match:
        raise Exception(f"Cannot extract a date from index name '{index_name}'")
    return date_match.group(1)


def git(*args, **kwargs):
    """Run a Git command and return its output."""
    subprocess.check_call(["git"] + list(args), **kwargs)


def get_dynamodb_items(sess, *, TableName, **kwargs):
    """
    Generates all the items in a DynamoDB table.

    :param sess: A boto3 Session which can read this table.
    :param TableName: The name of the table to scan.

    Other keyword arguments will be passed directly to the Scan operation.
    See https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/dynamodb.html#DynamoDB.Client.scan

    This does a Parallel Scan operation over the table.

    From https://alexwlchan.net/2020/getting-every-item-from-a-dynamodb-table-with-python/

    """
    dynamo_client = sess.resource("dynamodb").meta.client

    # How many segments to divide the table into?  As long as this is >= to the
    # number of threads used by the ThreadPoolExecutor, the exact number doesn't
    # seem to matter.
    total_segments = 25

    # How many scans to run in parallel?  If you set this really high you could
    # overwhelm the table read capacity, but otherwise I don't change this much.
    max_scans_in_parallel = 5

    # Schedule an initial scan for each segment of the table.  We read each
    # segment in a separate thread, then look to see if there are more rows to
    # read -- and if so, we schedule another scan.
    tasks_to_do = [
        {
            **kwargs,
            "TableName": TableName,
            "Segment": segment,
            "TotalSegments": total_segments,
        }
        for segment in range(total_segments)
    ]

    # Make the list an iterator, so the same tasks don't get run repeatedly.
    scans_to_run = iter(tasks_to_do)

    with concurrent.futures.ThreadPoolExecutor() as executor:
        # Schedule the initial batch of futures.  Here we assume that
        # max_scans_in_parallel < total_segments, so there's no risk that
        # the queue will throw an Empty exception.
        futures = {
            executor.submit(dynamo_client.scan, **scan_params): scan_params
            for scan_params in itertools.islice(scans_to_run, max_scans_in_parallel)
        }

        while futures:
            # Wait for the first future to complete.
            done, _ = concurrent.futures.wait(
                futures, return_when=concurrent.futures.FIRST_COMPLETED
            )

            for fut in done:
                yield from fut.result()["Items"]

                scan_params = futures.pop(fut)

                # A Scan reads up to N items, and tells you where it got to in
                # the LastEvaluatedKey.  You pass this key to the next Scan operation,
                # and it continues where it left off.
                try:
                    scan_params["ExclusiveStartKey"] = fut.result()["LastEvaluatedKey"]
                except KeyError:
                    break
                tasks_to_do.append(scan_params)

            # Schedule the next batch of futures.  At some point we might run out
            # of entries in the queue if we've finished scanning the table, so
            # we need to spot that and not throw.
            for scan_params in itertools.islice(scans_to_run, len(done)):
                futures[
                    executor.submit(dynamo_client.scan, **scan_params)
                ] = scan_params
