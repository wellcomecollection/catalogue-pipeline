import os
import random
import numpy as np
from elasticsearch import Elasticsearch, helpers

# The fields in the image documents which contain the feature vectors
FEATURE_VECTOR_FIELDS = ["inferredData.features1", "inferredData.features2"]


def get_random_documents(es_client, index_name, n):
    docs_count = es_client.count(index=index_name)["count"]
    iterator = helpers.scan(
        client=es_client,
        index=index_name,
        query={
            "query": {"match_all": {}},
            "_source": FEATURE_VECTOR_FIELDS
        }
    )
    iterator_indices = set(random.sample(range(docs_count), n))
    sample = [doc for n, doc in enumerate(iterator) if n in iterator_indices]

    return sample


def get_random_feature_vectors(n_documents):
    es_client = Elasticsearch(
        host=os.environ["ES_HOST"],
        port=os.environ["ES_PORT"],
        http_auth=(os.environ["ES_USERNAME"], os.environ["ES_PASSWORD"]),
        scheme=os.environ["ES_PROTOCOL"],
        use_ssl=(os.getenv("ES_PROTOCOL", "https") == "https")
    )

    print(f"Fetching {n_documents} random documents")
    documents = get_random_documents(
        es_client, os.environ["ES_INDEX"], n_documents
    )

    docs = [doc["_source"]["doc"] for doc in documents["docs"]]
    feature_vectors = np.stack(
        [
            np.concatenate([doc[field] for field in FEATURE_VECTOR_FIELDS], axis=0)
            for doc in docs
        ]
    )

    return feature_vectors
