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


def get_api_es_client():
    """
    Returns an Elasticsearch client for the catalogue cluster.
    """
    session = get_session(role_arn="arn:aws:iam::756629837203:role/catalogue-developer")

    host = get_secret_string(
        session, secret_id="elasticsearch/pipeline_storage_2022-10-03/public_host"
    )
    port = get_secret_string(session, secret_id="elasticsearch/pipeline_storage_2022-10-03/port")
    protocol = get_secret_string(
        session, secret_id="elasticsearch/pipeline_storage_2022-10-03/protocol"
    )
    username = get_secret_string(
        session, secret_id="elasticsearch/pipeline_storage_2022-10-03/catalogue_api/es_username"
    )
    password = get_secret_string(
        session, secret_id="elasticsearch/pipeline_storage_2022-10-03/catalogue_api/es_password"
    )

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")
