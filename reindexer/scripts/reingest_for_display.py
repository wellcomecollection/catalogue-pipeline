#!/usr/bin/env python3

import click
from elasticsearch.helpers import scan
from tqdm import tqdm

from get_reindex_status import get_pipeline_storage_es_client, get_session_with_role


@click.command()
@click.argument("reindex_date")
@click.option(
    "--type", "document_type", type=click.Choice(["works", "images"]), default="works"
)
@click.option("--test-doc-id", type=str)
def main(reindex_date, document_type, test_doc_id):
    es = get_pipeline_storage_es_client(reindex_date)
    session = get_session_with_role("arn:aws:iam::760097843905:role/platform-developer")
    sns = session.client("sns")

    api_index = f"{document_type}-indexed-{reindex_date}"
    dest_topic_arn_prefix = (
        f"arn:aws:sns:eu-west-1:760097843905:catalogue-{reindex_date}"
    )

    if document_type == "works":
        dest_topic_arn = f"{dest_topic_arn_prefix}_relation_embedder_output"
        api_index_query = {"term": {"type": "Visible"}}
    elif document_type == "images":
        dest_topic_arn = f"{dest_topic_arn_prefix}_image_inferrer_output"
        api_index_query = {"match_all": {}}

    count_response = es.count(index=api_index, query=api_index_query)
    reingest_docs_count = count_response["count"]

    if test_doc_id:
        print(f"Total visible documents: {reingest_docs_count}")
        print(f"Reingesting single test document {test_doc_id}")
        sns.publish(TopicArn=dest_topic_arn, Message=test_doc_id)
        print("Done!")
        return

    print(f"Reingesting {reingest_docs_count} documents...")
    for hit in tqdm(
        scan(
            es,
            scroll="15m",
            index=api_index,
            query={"query": api_index_query},
            _source=False,
        ),
        total=reingest_docs_count,
    ):
        doc_id = hit["_id"]
        sns.publish(TopicArn=dest_topic_arn, Message=doc_id)


if __name__ == "__main__":
    main()
