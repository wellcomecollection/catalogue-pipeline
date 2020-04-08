import boto3


def get_s3_client():
    try:
        s3_client = boto3.client("s3")
    except ClientError as e:
        logger.error(f"Failed to create s3 client: {e}")
        raise e
    return s3_client


def put_object_to_s3(binary_object, key, bucket_name):
    s3_client = get_s3_client()
    print("Uploading object to S3...")
    s3_client.put_object(Bucket=bucket_name, Key=key, Body=binary_object)
    print("Uploaded object to S3.")


def put_ssm_parameter(path, value, description):
    ssm_client = boto3.client("ssm")

    print(f"Updating SSM path `{path}` to `{value}`...")
    ssm_client.put_parameter(
        Name=path, Description=description, Value=value, Type="String", Overwrite=True
    )
    print("Updated SSM.")
