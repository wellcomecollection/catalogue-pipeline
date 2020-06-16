import os
import random
import numpy as np
from elasticsearch import Elasticsearch, helpers


def get_documents_from_es_in_batches(es_client, index_name, ids, batch_size=5000):
    id_batches = [ids[i : i + batch_size] for i in range(0, len(ids), batch_size)]

    docs = []
    for id_batch in id_batches:
        docs.extend = es_client.mget(
            index=index_name, body={"ids": id_batch}, request_timeout=120.0
        )["docs"]

    return docs


def get_random_documents(es_client, index_name, n):
    docs_count = es_client.count(index=index_name)["count"]
    id_iterator = helpers.scan(
        client=es_client,
        index=index_name,
        query={"query": {"match_all": {}}, "stored_fields": []},
    )
    iterator_indices = set(random.sample(range(docs_count), n))
    ids_sample = [
        doc["_id"] for n, doc in enumerate(id_iterator) if n in iterator_indices
    ]
    docs = get_documents_from_es_in_batches(es_client, index_name, ids_sample)
    return docs


def get_random_feature_vectors(n_documents):
    es_client = Elasticsearch(
        host=os.environ["ES_HOST"],
        port=os.environ["ES_PORT"],
        http_auth=(os.environ["ES_USERNAME"], os.environ["ES_PASSWORD"]),
        scheme=os.environ["ES_PROTOCOL"],
        use_ssl=(os.getenv("ES_PROTOCOL", "https") == "https"),
    )

    print(f"Fetching {n_documents} random documents")
    documents = get_random_documents(es_client, os.environ["ES_INDEX"], n_documents)

    feature_vectors = np.stack(
        [
            np.concatenate(
                [
                    doc["_source"]["inferredData"]["features1"],
                    doc["_source"]["inferredData"]["features2"],
                ],
                axis=0,
            )
            for doc in documents
        ]
    )

    return feature_vectors
