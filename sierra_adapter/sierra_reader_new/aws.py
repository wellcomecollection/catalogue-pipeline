import boto3


def get_secret_string(sess, **kwargs):
    secrets_client = sess.client("secretsmanager")

    return secrets_client.get_secret_value(**kwargs)["SecretString"]
