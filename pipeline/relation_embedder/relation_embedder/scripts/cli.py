import boto3
import os
import sys
import subprocess


def get_secret_string(session, secret_id):
    """
    Look up the value of a SecretString in Secrets Manager.
    """
    secrets = session.client("secretsmanager")
    return secrets.get_secret_value(SecretId=secret_id)["SecretString"]


def set_environment(pipeline_date):
    session = boto3.Session()
    prefix = f"elasticsearch/pipeline_storage_{pipeline_date}/"
    os.environ["es_host"] = get_secret_string(session, f"{prefix}public_host")
    os.environ["es_apikey"] = get_secret_string(session, f"{prefix}read_only/api_key")
    os.environ["es_port"] = get_secret_string(session, f"{prefix}port")
    os.environ["es_protocol"] = get_secret_string(session, f"{prefix}protocol")
    os.environ["es_merged_index"] = f"works-merged-{pipeline_date}"


set_environment("2024-11-18")
# from elasticsearch import Elasticsearch
#
# es = Elasticsearch([f"https://{os.environ['es_host']}:{os.environ['es_port']}"], api_key=os.environ["es_apikey"])
# print(es.count())
subprocess.run(sys.argv[1], input=sys.stdin.read(), text=True)
