from elasticsearch import Elasticsearch

matcher_index = "works-identified"


def get_secret_string(session, *, secret_id):
    """
    Look up the value of a SecretString in Secrets Manager.
    """
    secrets = session.client("secretsmanager")
    return secrets.get_secret_value(SecretId=secret_id)["SecretString"]


def get_pipeline_storage_es_client(session, *, index_date):
    """
    Returns an Elasticsearch client for the pipeline-storage cluster.
    """
    secret_prefix = f"elasticsearch/pipeline_storage_{index_date}"

    host = get_secret_string(session, secret_id=f"{secret_prefix}/public_host")
    port = get_secret_string(session, secret_id=f"{secret_prefix}/port")
    protocol = get_secret_string(session, secret_id=f"{secret_prefix}/protocol")
    username = get_secret_string(
        session, secret_id=f"{secret_prefix}/read_only/es_username"
    )
    password = get_secret_string(
        session, secret_id=f"{secret_prefix}/read_only/es_password"
    )

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


def get_nodes_properties(es, *, index_date, work_ids):
    response = es.mget(
        index=f"{matcher_index}-{index_date}",
        body={"ids": work_ids},
        doc_type="_doc",
        _source=[
            "state.sourceIdentifier.identifierType.id",
            "state.sourceIdentifier.value",
            "type"
        ],
    )
    return [
        {
            "id": doc["_id"],
            "source_id_type": doc["_source"]["state"]["sourceIdentifier"]["identifierType"]["id"],
            "source_id": doc["_source"]["state"]["sourceIdentifier"]["value"],
            "type": doc["_source"]["type"]
        }
        for doc in response["docs"]
    ]
