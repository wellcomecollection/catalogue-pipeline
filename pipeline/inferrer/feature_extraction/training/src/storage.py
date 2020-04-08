from .aws import put_object_to_s3, put_ssm_parameter


def store_model(object_binary, name, prefix, bucket_name):
    object_key = f"{prefix}/{name}.pkl"
    put_object_to_s3(
        binary_object=object_binary,
        key=object_key,
        bucket_name=bucket_name,
    )
    put_ssm_parameter(
        path=f"/catalogue_pipeline/config/inferrer/model_object/{prefix}",
        value=object_key,
        description="S3 object key for the feature model"
    )
