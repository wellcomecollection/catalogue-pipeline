import boto3
import click
import json

from .deletion_check_initiator import DeletionCheckInitiator

session = boto3.Session(profile_name="platform-developer")


def read_from_s3(bucket, key):
    s3 = session.client("s3")
    obj = s3.get_object(Bucket=bucket, Key=key)
    return obj["Body"].read()


def get_reindexer_topic_arn():
    statefile_body = read_from_s3(
        bucket="wellcomecollection-platform-infra",
        key="terraform/catalogue/reindexer.tfstate",
    )

    # The structure of the interesting bits of the statefile is:
    #
    #   {
    #       ...
    #       "outputs": {
    #          "name_of_output": {
    #              "value": "1234567890x",
    #              ...
    #          },
    #          ...
    #      }
    #   }
    #
    statefile_data = json.loads(statefile_body)
    outputs = statefile_data["outputs"]
    return outputs["topic_arn"]["value"]


@click.group()
@click.option("--table-name", default="calm-vhs-adapter")
@click.option("--topic-arn", default=get_reindexer_topic_arn)
@click.pass_context
def cli(ctx, topic_arn, table_name):
    dynamo_client = session.client("dynamodb")
    sns_client = session.client("sns")
    ctx.obj = DeletionCheckInitiator(
        dynamo_client=dynamo_client,
        sns_client=sns_client,
        reindexer_topic_arn=topic_arn,
        source_table_name=table_name
    )


@cli.command()
@cli.pass_obj
def complete(deletion_check_initiator):
    deletion_check_initiator.all_records()


@cli.command()
@cli.argument("targets", nargs=-1)
def targeted(deletion_check_initiator, targets):
    deletion_check_initiator.specific_records(list(targets))


if __name__ == "__main__":
    cli()
