#!/usr/bin/env python3
"""
This is a crude script for analysing errors in the transformer DLQs.

It:

*   saves a list of all messages from the DLQ
*   queries the logging cluster for errors with the same ID

It's quite rough and scrappy; this is fine, it's meant for quick debugging,
not to be the linchpin of a major system.

"""

import datetime
import json
import re
import sys

import boto3
from elasticsearch import Elasticsearch


DEVELOPER_ROLE_ARN = "arn:aws:iam::760097843905:role/platform-developer"


def get_boto3_session(*, role_arn):
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


def get_messages_from_queue(sess, queue_url):
    sqs_client = sess.client("sqs")

    while True:
        resp = sqs_client.receive_message(
            QueueUrl=queue_url, AttributeNames=["All"], MaxNumberOfMessages=10
        )

        try:
            yield from resp["Messages"]
        except KeyError:
            break

        entries = [
            {"Id": msg["MessageId"], "ReceiptHandle": msg["ReceiptHandle"]}
            for msg in resp["Messages"]
        ]

        sqs_client.delete_message_batch(QueueUrl=queue_url, Entries=entries)


def save_dlq_locally(sess, *, out_file):
    with open(out_file, "a") as outfile:
        for transformer in ("calm", "mets", "miro", "sierra", "tei"):
            dlq_url = f"https://sqs.eu-west-1.amazonaws.com/760097843905/catalogue-{pipeline_date}_transformer_{transformer}_input_dlq"

            for message in get_messages_from_queue(sess, queue_url=dlq_url):
                payload = json.loads(json.loads(message["Body"])["Message"])

                sent = datetime.datetime.fromtimestamp(
                    int(message["Attributes"]["SentTimestamp"]) / 1000
                )

                outfile.write(
                    json.dumps(
                        {
                            "payload": payload,
                            "transformer": transformer,
                            "sent": sent.isoformat(),
                        }
                    )
                    + "\n"
                )


def get_secret_string(sess, *, secret_id):
    secrets_client = sess.client("secretsmanager")
    return secrets_client.get_secret_value(SecretId=secret_id)["SecretString"]


def get_logging_client(sess):
    host = get_secret_string(sess, secret_id="elasticsearch/logging/public_host")
    username = get_secret_string(
        sess, secret_id="elasticsearch/logging/management_username"
    )
    password = get_secret_string(
        sess, secret_id="elasticsearch/logging/management_password"
    )

    return Elasticsearch(f"https://{username}:{password}@{host}")


def clean_log_line(line):
    _, line = line.split(" ERROR ", 1)

    line = line.replace(
        "w.p.transformer.TransformerWorker - TransformerWorker:", ""
    ).strip()

    # Present a more human-friendly explanation for errors like:
    #
    #       TransformerError on MetsFileWithImages(…) with Version(b31851125,1)
    #       (java.lang.Exception: Couldn't match creativecommons.org/… to a license)
    #
    if line.startswith("TransformerError on MetsFileWithImages") and line.endswith(
        "to a license)"
    ):
        match = re.search(
            r"java\.lang\.Exception: Couldn't match (?P<url>[^\s]+) to a license", line
        )
        if match is not None:
            return f"bad license URL: {match.group('url')}"

    return line


def get_log_lines(logging_client, *, pipeline_date, source_record):
    service_name = (
        f"catalogue-{pipeline_date}_transformer_{source_record['transformer']}"
    )

    resp = logging_client.search(
        index="firelens-*",
        body={
            "query": {
                "bool": {
                    "must": [
                        # Note: it should be possible to get this working in
                        # a filter context, but I ran out of time.
                        {"match": {"service_name": service_name}},
                        {"match": {"log": source_record["payload"]["id"]}},
                    ]
                }
            },
            "size": 100,
        },
    )

    log_lines = {
        clean_log_line(hit["_source"]["log"])
        for hit in resp["hits"]["hits"]
        if hit["_source"]["service_name"] == service_name
    }

    return log_lines


if __name__ == "__main__":
    try:
        pipeline_date = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <PIPELINE_DATE>")

    out_file = f"transformer_dlq_{pipeline_date}.txt"

    sess = get_boto3_session(role_arn=DEVELOPER_ROLE_ARN)

    save_dlq_locally(sess, out_file=out_file)

    logging_client = get_logging_client(sess)

    for line in open(out_file):
        source_record = json.loads(line)

        log_lines = get_log_lines(
            logging_client, pipeline_date=pipeline_date, source_record=source_record
        )

        if len(log_lines) == 1:
            print(
                f"{source_record['transformer']} {source_record['payload']['id']}: {list(log_lines)[0]}"
            )
            continue

        print(f"== {source_record['transformer']} {source_record['payload']['id']} ==")

        for line in log_lines:
            print(line)

        print("")
