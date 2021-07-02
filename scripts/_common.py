import boto3
import re
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


def get_pipeline_es_client(session, *, pipeline_date, action, doc_type):
    """
    Returns an Elasticsearch client for the catalogue cluster.
    """
    secret_path = f"elasticsearch/pipeline_storage_{pipeline_date}"

    if doc_type not in ["work", "image"]:
        raise Exception("doc_type must be 'work' or 'image'")

    if action == "read":
        user = "read_only"
    elif action == "write":
        user = f"{doc_type}_ingestor"
    else:
        raise Exception("action must be 'read' or 'write'")

    host = get_secret_string(session, secret_id=f"{secret_path}/public_host")
    port = get_secret_string(session, secret_id=f"{secret_path}/port")
    protocol = get_secret_string(session, secret_id=f"{secret_path}/protocol")
    username = get_secret_string(session, secret_id=f"{secret_path}/{user}/es_username")
    password = get_secret_string(session, secret_id=f"{secret_path}/{user}/es_password")

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


def get_date_from_index_name(index_name):
    date_match = re.search(r"-(\d{4}-\d{2}-\d{2})$", index_name)
    if not date_match:
        raise Exception(f"Cannot extract a date from index name '{index_name}'")
    return date_match.group(1)
