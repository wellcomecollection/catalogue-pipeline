#!/usr/bin/env python

import itertools
import json
import math
import sys

import boto3
import click
import tqdm

SOURCES = {
    "miro": "vhs-sourcedata-miro",
    "sierra": "vhs-sierra-sierra-adapter-20200604",
    "mets": "mets-adapter-store-delta",
    "calm": "vhs-calm-adapter",
    "tei": "tei-adapter-store",
}

DESTINATIONS = ["catalogue", "catalogue_miro_updates", "reporting"]


def how_many_segments(table_name):
    """
    When we do a complete reindex, we need to tell the reindexer how many segments
    to use.  Each segment should contain ~1000 records, so we don't exhaust the
    memory in the reindexer.

    (The reindexer loads the contents of each segment into memory, so choosing overly
    large segment sizes causes it to fall over.)

    """
    dynamodb = session.client("dynamodb")
    resp = dynamodb.describe_table(TableName=table_name)

    # The item count isn't real-time; it gets updated every six hours or so.
    # In practice it's the right order of magnitude: if the table has lots of churn,
    # it's probably a bad time to reindex!
    try:
        item_count = resp["Table"]["ItemCount"]
    except KeyError:
        sys.exit("No such table {table_name!r}?")

    return int(math.ceil(item_count / 900))


def complete_reindex_parameters(total_segments):
    for segment in range(total_segments):
        yield {
            "segment": segment,
            "totalSegments": total_segments,
            "type": "CompleteReindexParameters",
        }


def partial_reindex_parameters(max_records):
    yield {"maxRecords": max_records, "type": "PartialReindexParameters"}


def chunked_iterable(iterable, size):
    # See https://alexwlchan.net/2018/12/iterating-in-fixed-size-chunks/
    it = iter(iterable)
    while True:
        chunk = tuple(itertools.islice(it, size))
        if not chunk:
            break
        yield chunk


def specific_reindex_parameters(record_ids):
    # The reindexer can handle up to 100 IDs at a time, so send them in
    # batches of that size.
    for chunk in chunked_iterable(record_ids, size=100):
        yield {"ids": chunk, "type": "SpecificReindexParameters"}


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


def publish_messages(job_config_id, topic_arn, parameters):
    """Publish a sequence of messages to an SNS topic."""
    sns = session.client("sns")
    for params in tqdm.tqdm(list(parameters)):
        to_publish = {"jobConfigId": job_config_id, "parameters": params}
        resp = sns.publish(
            TopicArn=topic_arn,
            MessageStructure="json",
            Message=json.dumps({"default": json.dumps(to_publish)}),
            Subject=f"Source: {__file__}",
        )
        assert resp["ResponseMetadata"]["HTTPStatusCode"] == 200, resp


def get_reindexer_job_config(session):
    """
    Get the reindexer job config passed to the reindexer task.
    """
    ecs_client = session.client("ecs")

    resp = ecs_client.describe_task_definition(taskDefinition="reindexer")

    # The container definition contains two containers: the reindexer app, and the
    # logstash router.
    container_definition = next(
        cd
        for cd in resp["taskDefinition"]["containerDefinitions"]
        if cd["name"] == "reindexer"
    )

    job_config_str = next(
        ev["value"]
        for ev in container_definition["environment"]
        if ev["name"] == "reindexer_job_config_json"
    )

    return json.loads(job_config_str)


def has_subscriptions(session, *, topic_arn):
    """
    Returns True if a topic ARN has any subscriptions (e.g. an SQS queue), False otherwise.
    """
    sns_client = session.client("sns")
    resp = sns_client.list_subscriptions_by_topic(TopicArn=topic_arn)

    return len(resp["Subscriptions"]) > 0


def verify_specific_ids(*, source, specific_ids):
    # The IDs in the Sierra table are the seven-digit IDs (e.g. 3272507),
    # but they appear in multiple forms (e.g. b32725073) that can't be
    # reindexed.  If somebody tries to reindex the wrong form, warn them!
    if source == "sierra":
        bad_ids = [id for id in specific_ids if len(id) != 7 or not id.isnumeric()]
        if bad_ids:
            raise ValueError(f"Sierra IDs should be 7-digit numeric IDs, got {bad_ids}")


@click.command()
@click.option(
    "--src",
    # To run a reindex, avoiding the creation of unwanted Image records,
    # we need to first run everything that is not miro, then once that
    # has all made it through the matcher/merger, run miro.
    type=click.Choice(["all"] + ["notmiro"] + list(SOURCES.keys())),
    required=True,
    prompt="Which source do you want to reindex?",
    help="Name of the source to reindex, or all to reindex all sources",
)
@click.option(
    "--dst",
    type=click.Choice(DESTINATIONS),
    required=True,
    prompt="Which pipeline are you sending this to?",
    help="Name of the pipeline to receive the reindexed records",
)
@click.option(
    "--mode",
    type=click.Choice(["complete", "partial", "specific"]),
    required=True,
    prompt="Every record (complete), just a few (partial), or specific records (specific)?",
    help="Should this reindex send every record (complete), just a few (partial), or specific records (specific)?",
)
@click.pass_context
def start_reindex(ctx, src, dst, mode):
    if src in ["all", "notmiro"]:
        if mode != "complete":
            sys.exit("All-source reindexes only support --mode=complete")

        if src == "all":
            warning_message = (
                f"Warning: Sending Miro at the same time as everything else "
                "may result in the erroneous creation of Image records.\nContinue?"
            )
            click.confirm(click.style(warning_message, "yellow"), abort=True)

        for source in SOURCES.keys():
            if source == "miro" and src == "notmiro":
                continue
            ctx.invoke(start_reindex, src=source, dst=dst, mode=mode)
            print("")
        return

    print(f"Starting a reindex {src} ~> {dst}")

    if mode == "complete":
        total_segments = how_many_segments(table_name=SOURCES[src])
        parameters = complete_reindex_parameters(total_segments)
    elif mode == "partial":
        max_records = click.prompt("How many records do you want to send?", default=10)
        parameters = partial_reindex_parameters(max_records)
    elif mode == "specific":
        specified_records_str = click.prompt(
            "Which records do you want to reindex? (separate multiple IDs with spaces)",
            type=str,
        )
        specified_records = specified_records_str.split()
        if not specified_records:
            return sys.exit("You need to specify at least 1 record ID")

        verify_specific_ids(source=src, specific_ids=specified_records)
        parameters = specific_reindex_parameters(specified_records)

    job_config_id = f"{src}--{dst}"
    reindexer_job_config = get_reindexer_job_config(session)

    # It's incredibly frustrating to run a reindex using this script, see nothing come
    # through the pipeline, and realise that nothing is listening to the reindexer output.
    # (Ask me how I know.)
    #
    # This probably isn't what the user is trying to do, so if we detect there's nothing
    # subscribed to the reindexer output, double-check that's correct.  It will save us
    # time, frustration, and money.
    try:
        job_config = reindexer_job_config[job_config_id]
    except KeyError:
        raise RuntimeError(f"Unrecognised job config ID: {job_config_id}")
    else:
        destination_topic_arn = job_config["destinationConfig"]["topicArn"]

        if not has_subscriptions(session, topic_arn=destination_topic_arn):
            topic_name = destination_topic_arn.split(":")[-1]
            warning_message = (
                f"Warning: This reindex will send records to {topic_name}, "
                "but nothing is subscribed to that topic.\nContinue?"
            )
            click.confirm(click.style(warning_message, "yellow"), abort=True)

    reindexer_topic_arn = get_reindexer_topic_arn()

    publish_messages(
        job_config_id=job_config_id,
        topic_arn=reindexer_topic_arn,
        parameters=parameters,
    )

    start_reindexer_tasks(session)


def start_reindexer_tasks(sess):
    """
    To make the reindex start immediately, update the desired count of
    the reindexer service.

    If we wait for the reindexer to start based on our regular autoscaling,
    we have to wait up to 15 minutes for the queue to move out of the
    inactive state.  This is mildly annoying.

    See https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-monitoring-using-cloudwatch.html

    """
    ecs_client = sess.client("ecs")

    resp = ecs_client.describe_services(cluster="reindexer", services=["reindexer"])
    service = resp["services"][0]

    if service["desiredCount"] >= 3:
        return

    ecs_client.update_service(cluster="reindexer", service="reindexer", desiredCount=3)


if __name__ == "__main__":
    sts = boto3.client("sts")
    response = sts.assume_role(
        RoleArn="arn:aws:iam::760097843905:role/platform-developer",
        RoleSessionName="platform",
    )
    session = boto3.Session(
        aws_access_key_id=response["Credentials"]["AccessKeyId"],
        aws_secret_access_key=response["Credentials"]["SecretAccessKey"],
        aws_session_token=response["Credentials"]["SessionToken"],
        region_name="eu-west-1",
    )

    start_reindex()
