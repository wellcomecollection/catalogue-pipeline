#!/usr/bin/env python3

"""
Injects a list of IDs as messages on a pipeline topic

See the section of the README "fixing leaks" for more info.
"""
import click
from tqdm import tqdm
from uuid import uuid4
from get_reindex_status import get_session_with_role
from concurrent.futures import ThreadPoolExecutor


def build_sns_batch(messages):
    for m in messages:
        yield {"Id": str(uuid4()), "Message": m, "MessageStructure": "string"}


def publish_batch_to_sns(sns, topic_arn, messages):
    batch_messages = build_sns_batch(messages)
    response = sns.publish_batch(
        TopicArn=topic_arn, PublishBatchRequestEntries=list(batch_messages)
    )
    return response


def publish_to_sns_threaded(sns, topic_arn, doc_ids, sns_batch_size=10):
    with ThreadPoolExecutor(max_workers=10) as executor:
        batched_messages = [
            doc_ids[i : i + sns_batch_size]
            for i in range(0, len(doc_ids), sns_batch_size)
        ]
        results = list(
            executor.map(
                lambda batch: publish_batch_to_sns(sns, topic_arn, batch),
                batched_messages,
            )
        )
    return results


def inject_id_messages(session, *, destination_name, reindex_date, ids):
    sns = session.client("sns")

    topic_arn = f"arn:aws:sns:eu-west-1:760097843905:catalogue-{reindex_date}_{destination_name}"

    chunk_size = 10000
    chunks = [ids[i : i + chunk_size] for i in range(0, len(ids), chunk_size)]

    for chunk in tqdm(chunks):
        results = publish_to_sns_threaded(sns, topic_arn, chunk)
        for result in results:
            if result["ResponseMetadata"]["HTTPStatusCode"] != 200:
                print(f"Failed to publish!")


@click.command()
@click.argument("reindex_date")
@click.argument("destination_name")
@click.argument("ids_file_path")
def main(reindex_date, destination_name, ids_file_path):
    session_dev = get_session_with_role(
        "arn:aws:iam::760097843905:role/platform-developer"
    )
    with open(ids_file_path) as ids_file:
        ids = [line.rstrip() for line in ids_file]

    print(f"Injecting {len(ids)} messages into {reindex_date}_{destination_name}")
    inject_id_messages(
        session_dev,
        destination_name=destination_name,
        reindex_date=reindex_date,
        ids=ids,
    )


if __name__ == "__main__":
    main()
