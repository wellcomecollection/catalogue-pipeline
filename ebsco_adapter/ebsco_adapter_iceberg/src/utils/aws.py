from typing import cast

import boto3


def get_ssm_parameter(parameter_name: str) -> str:
    """Returns an AWS SSM parameter string associated with a given name."""
    ssm_client = boto3.Session().client("ssm")
    response = ssm_client.get_parameter(Name=parameter_name)
    return cast(str, response["Parameter"]["Value"])
