import os

import numpy as np
from elasticsearch import Elasticsearch, helpers


def get_all_ids(es_client, index_name):
    response = helpers.scan(
        client=es_client,
        index=index_name,
        query={
            "query": {
                "match_all": {}
            },
            "stored_fields": []
        }
    )

    return [hit['_id'] for hit in list(response)]


def get_random_documents(es_client, index_name, n):
    all_ids = get_all_ids(es_client, index_name)
    if len(all_ids) > n:
        query_ids = np.random.choice(all_ids, n, replace=False).tolist()
    else:
        query_ids = all_ids

    response = es_client.mget(
        index=index_name,
        body={'ids': query_ids}
    )

    return response


def get_random_feature_vectors(n_documents):
    es_client = Elasticsearch(
        host=os.environ['ES_HOST'],
        http_auth=(os.environ['ES_USERNAME'], os.environ['ES_PASSWORD'])
    )

    documents = get_random_documents(
        es_client, os.environ['ES_INDEX_NAME'], n_documents
    )

    docs = [doc['_source']['doc'] for doc in documents['docs']]
    feature_vectors = np.stack([
        np.concatenate(
            [doc['feature_vector_1'], doc['feature_vector_2']],
            axis=0
        ) for doc in docs
    ])

    return feature_vectors
