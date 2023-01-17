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
    Returns an Elasticsearch client for the catalogue cluster.
    """
    session = get_session(role_arn="arn:aws:iam::756629837203:role/catalogue-developer")
    host, port, protocol = _get_pipeline_cluster(session, date=date)
    username = get_secret_string(
        session,
        secret_id=f"elasticsearch/pipeline_storage_{date}/catalogue_api/es_username",
    )
    password = get_secret_string(
        session,
        secret_id=f"elasticsearch/pipeline_storage_{date}/catalogue_api/es_password",
    )

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


def get_ingestor_es_client(date, doc_type):
    """
    Returns an Elasticsearch client for the catalogue cluster.
    """
    session = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")
    host, port, protocol = _get_pipeline_cluster(session, date=date)
    username = get_secret_string(
        session,
        secret_id=f"elasticsearch/pipeline_storage_{date}/{doc_type}_ingestor/es_username",
    )
    password = get_secret_string(
        session,
        secret_id=f"elasticsearch/pipeline_storage_{date}/{doc_type}_ingestor/es_password",
    )

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


def get_date_from_index_name(index_name):
    date_match = re.search(r"-(\d{4}-\d{2}-\d{2})$", index_name)
    if not date_match:
        raise Exception(f"Cannot extract a date from index name '{index_name}'")
    return date_match.group(1)
