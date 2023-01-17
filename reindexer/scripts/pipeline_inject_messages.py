#!/usr/bin/env python3

"""
Injects a list of IDs as messages on a pipeline topic

See the section of the README "fixing leaks" for more info.
"""
import click
from tqdm import tqdm
from get_reindex_status import get_session_with_role


def inject_id_messages(session, *, destination_name, reindex_date, ids):
    sns = session.client("sns")
    for id in tqdm(ids):
        sns.publish(
            TopicArn=f"arn:aws:sns:eu-west-1:760097843905:catalogue-{reindex_date}_{destination_name}",
            Message=id,
        )


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
