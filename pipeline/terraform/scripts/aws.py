import boto3
import click


def get_session(*, role_arn):
    """
    Get a boto3 client authenticated against the given role.
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


def read_secret(session, *, secret_id):
    """
    Retrieve a secret from Secrets Manager.
    """
    secrets_client = session.client("secretsmanager")
    return secrets_client.get_secret_value(SecretId=secret_id)["SecretString"]


def write_secret(session, *, secret_id, secret_value, description):
    """
    Store a key/value pair in Secrets Manager.
    """
    secrets_client = session.client("secretsmanager")

    resp = secrets_client.put_secret_value(
        SecretId=secret_id, SecretString=secret_value
    )

    if resp["ResponseMetadata"]["HTTPStatusCode"] != 200:
        raise RuntimeError(f"Unexpected error from PutSecretValue: {resp}")

    click.echo(f"Stored secret {click.style(secret_id, 'yellow')}")
