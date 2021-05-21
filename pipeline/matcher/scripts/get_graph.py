#!/usr/bin/env python3

import boto3
import click
import json
from graphviz import Digraph

from dynamo import get_graph_table_row, get_graph_component
from elastic import get_pipeline_storage_es_client, get_nodes_properties

source_type_labels = {
    "sierra-system-number": "Sierra",
    "miro-image-number": "Miro",
    "mets": "METS",
    "calm-record-id": "CALM",
}


def get_aws_session(*, role_arn):
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


@click.command()
@click.argument("index_date")
@click.argument("work_id")
@click.option("--emit-work-data", is_flag=True)
def main(index_date, work_id, emit_work_data):
    session = get_aws_session(
        role_arn="arn:aws:iam::760097843905:role/platform-read_only"
    )
    # Necessary for reading secrets
    dev_session = get_aws_session(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )
    es = get_pipeline_storage_es_client(dev_session, index_date=index_date)
    graph = Digraph(f"Matcher graph for {work_id}", format="pdf")

    try:
        initial_row = get_graph_table_row(
            session, index_date=index_date, work_id=work_id
        )
    except Exception:
        raise Exception("Could not find work in graph")

    component_id = initial_row["componentId"]
    graph_component = get_graph_component(
        session, index_date=index_date, component_id=component_id
    )

    node_ids = [node["id"] for node in graph_component]
    node_links = [node["linkedIds"] for node in graph_component]
    nodes = get_nodes_properties(
        es,
        index_date=index_date,
        work_ids=node_ids,
        fetch_complete_work=emit_work_data,
    )

    deleted_node_ids = {node["id"] for node in nodes if node["type"] == "Deleted"}
    valid_node_links = [
        [dest for dest in ids if dest not in deleted_node_ids] for ids in node_links
    ]
    for node, links in filter(
        lambda x: x[0]["type"] != "Deleted", zip(nodes, valid_node_links)
    ):
        source = source_type_labels.get(node["source_id_type"], node["source_id_type"])
        graph.node(node["id"], label=fr"{source}\n{node['source_id']}")
        graph.edges([(node["id"], dest) for dest in links])

    print(graph.source)
    staggered = graph.unflatten(stagger=3)
    staggered.render(f"{work_id}_graph", view=True, cleanup=True)

    if emit_work_data:
        work_data = {node["id"]: node["complete_work"] for node in nodes}
        work_data_filename = f"{work_id}_work_data.json"
        with open(work_data_filename, "w") as work_data_file:
            json.dump(work_data, work_data_file, indent=2)
        print(f"Wrote work data to {work_data_filename}")


if __name__ == "__main__":
    main()
