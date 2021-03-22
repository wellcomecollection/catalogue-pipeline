#!/usr/bin/env python3

import boto3
import click
from graphviz import Digraph

from dynamo import get_graph_table_row, get_graph_component
from elastic import get_pipeline_storage_es_client, get_nodes_properties

source_type_labels = {
    "sierra-system-number": "Sierra",
    "miro-image-number": "Miro",
    "mets": "METS",
    "calm-record-id": "CALM",
}


@click.command()
@click.argument("index_date")
@click.argument("work_id")
def main(index_date, work_id):
    session = boto3.Session(profile_name="platform-read_only")
    # Necessary for reading secrets
    dev_session = boto3.Session(profile_name="platform-developer")
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
    nodes = get_nodes_properties(es, index_date=index_date, work_ids=node_ids)

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


if __name__ == "__main__":
    main()
