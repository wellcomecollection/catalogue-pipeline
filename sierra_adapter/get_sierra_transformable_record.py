#!/usr/bin/env python

import json
import os
import sys
import tempfile

import boto3
import click


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


def get_user_input(argv):
    try:
        return argv[1]
    except IndexError:
        raise RuntimeError(f"Usage: {__file__} <BNUMBER>")


def get_bnumber_from_user_input(user_input):
    """
    The key used in the Sierra adapter VHS is the seven-digit form of a b-number.

    This function takes the user input, which could include the 'b' prefix or
    the check digit, and reduces it to the seven-digit form.
    """
    if (
        len(user_input) == len("b1234567x") or len(user_input) == len("b1234567")
    ) and user_input.startswith("b"):
        return user_input[1:8]
    elif len(user_input) == len("1234567") and user_input.isnumeric():
        return user_input
    else:
        raise ValueError(f"Not a valid bnumber: {user_input}")


def get_transformable(bnumber):
    session = get_session(role_arn="arn:aws:iam::760097843905:role/platform-read_only")

    dynamodb = session.resource("dynamodb")
    table = dynamodb.Table("vhs-sierra-sierra-adapter-20200604")

    try:
        item = table.get_item(Key={"id": bnumber})["Item"]
    except KeyError:
        raise RuntimeError(f"Unable to find a DynamoDB record with id={bnumber}")

    location = item["payload"]

    s3 = session.client("s3")
    s3_obj = s3.get_object(Bucket=location["bucket"], Key=location["key"])

    return json.load(s3_obj["Body"])


@click.command(help="Retrieve the JSON for a Sierra record in VHS.")
@click.argument("bnumber")
@click.option(
    "--skip-decoding",
    is_flag=True,
    help=(
        "Skip decoding the JSON in the bib/item records.\n"
        "Use this if you want to pass the output directly to a local transformer, and not inspect it by hand."
    ),
)
def main(bnumber, skip_decoding):
    try:
        bnumber = get_bnumber_from_user_input(bnumber)
        transformable = get_transformable(bnumber)

        # The SierraTransformable format stores the raw JSON responses from the
        # Sierra API, so we don't lose any data when decoding/encoding using our Scala models.
        # This is a pain to deal with if you want to inspect the JSON by hand, so
        # decode it before saving the result.
        if not skip_decoding:
            if transformable.get("maybeBibRecord") is not None:
                transformable["maybeBibRecord"]["data"] = json.loads(
                    transformable["maybeBibRecord"]["data"]
                )

            for item_record in transformable["itemRecords"].values():
                item_record["data"] = json.loads(item_record["data"])

            for holdings_record in transformable.get("holdingsRecords", {}).values():
                holdings_record["data"] = json.loads(holdings_record["data"])

            for order_record in transformable.get("orderRecords", {}).values():
                order_record["data"] = json.loads(order_record["data"])

        _, tmp_path = tempfile.mkstemp(suffix=".json", prefix=f"b{bnumber}_")
        with open(tmp_path, "w") as outfile:
            outfile.write(json.dumps(transformable, indent=2))

        print(tmp_path)
        os.system(f"open {tmp_path}")
    except Exception as exc:
        sys.exit(str(exc))


if __name__ == "__main__":
    main()
