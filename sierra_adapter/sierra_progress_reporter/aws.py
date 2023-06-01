def get_secret_string(sess, **kwargs):
    secrets_client = sess.client("secretsmanager")

    return secrets_client.get_secret_value(**kwargs)["SecretString"]


def list_s3_prefix(sess, *, bucket, prefix):
    """
    Generate the keys in an S3 bucket that match a given prefix.
    """
    s3_client = sess.client("s3")

    paginator = s3_client.get_paginator("list_objects")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        contents = page.get("Contents", [])

        print("  Got page of %d objects from S3…" % len(contents))
        for s3_obj in contents:
            yield s3_obj["Key"]
