#!/usr/bin/env python3
"""
Reindex one Elasticsearch index into another on a pipeline-storage cluster, and verify
doc counts.

Both indices must live on the same ``pipeline_storage_<pipeline_date>`` cluster.

By default the reindex runs with ``version_type=external`` and ``conflicts=proceed`` so it
never overwrites a fresher write at the destination and is safe to re-run (e.g. to catch
up after a writer has been moved onto the destination index). Pass ``--verify-field`` to
also report how many docs have that field *indexed* (an ``exists`` query) in each index —
useful when the point of the reindex is to populate a newly-mapped field (a
``dynamic:false`` mapping leaves unmapped fields in ``_source`` but unqueryable, so the
count is 0 until the docs are reindexed under a mapping that indexes the field).

A write (``_reindex``) needs more than read-only privileges, so this connects with the
deployment superuser credentials (``.../es_username`` + ``.../es_password``), not the
``read_only`` user used by get_reindex_status.py.

Usage (from reindexer/scripts/):
    uv run reindex_index.py --source-index SRC --dest-index DST
    uv run reindex_index.py --source-index SRC --dest-index DST --verify-field modifiedTime
    uv run reindex_index.py --source-index SRC --dest-index DST --verify-only   # read-only counts
    uv run reindex_index.py --source-index SRC --dest-index DST --no-wait        # fire and exit
"""

import time

import click
from elasticsearch import Elasticsearch

from get_reindex_status import get_secret_string, get_session_with_role


def get_superuser_es_client(pipeline_date: str) -> Elasticsearch:
    """
    Returns an Elasticsearch client for the pipeline-storage cluster authenticated as the
    deployment superuser (required for _reindex, which writes).
    """
    session = get_session_with_role(
        "arn:aws:iam::760097843905:role/platform-developer"
    )

    secret_prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"

    host = get_secret_string(session, secret_id=f"{secret_prefix}/public_host")
    port = get_secret_string(session, secret_id=f"{secret_prefix}/port")
    protocol = get_secret_string(session, secret_id=f"{secret_prefix}/protocol")
    username = get_secret_string(session, secret_id=f"{secret_prefix}/es_username")
    password = get_secret_string(session, secret_id=f"{secret_prefix}/es_password")

    return Elasticsearch(
        f"{protocol}://{host}:{port}",
        basic_auth=(username, password),
        request_timeout=120,
    )


def verify(
    es_client: Elasticsearch,
    *,
    indices: list[str],
    verify_field: str | None,
) -> None:
    """
    Print the total doc count for each index, and (if verify_field is given) how many docs
    have that field indexed/queryable via an ``exists`` query.
    """
    for index in indices:
        if not es_client.indices.exists(index=index):
            click.echo(f"  {index}: (does not exist)")
            continue
        total = es_client.count(index=index)["count"]
        line = f"  {index}: total={total}"
        if verify_field:
            try:
                queryable = es_client.count(
                    index=index, query={"exists": {"field": verify_field}}
                )["count"]
                line += f", {verify_field}-indexed={queryable}"
            except Exception as exc:  # noqa: BLE001 - report unmapped-field errors inline
                line += f", {verify_field}-indexed=(error: {type(exc).__name__})"
        click.echo(line)


@click.command()
@click.option("--pipeline-date", default="2025-10-02", show_default=True,
              help="Selects the pipeline_storage_<date> cluster to connect to.")
@click.option("--source-index", required=True, help="Index to reindex from.")
@click.option("--dest-index", required=True, help="Index to reindex into.")
@click.option("--version-type", default="external", show_default=True,
              type=click.Choice(["external", "internal"]),
              help="external = never clobber a fresher dest doc; safe to re-run.")
@click.option("--conflicts", default="proceed", show_default=True,
              type=click.Choice(["proceed", "abort"]))
@click.option("--verify-field", default=None,
              help="Also report how many docs have this field indexed (exists query).")
@click.option("--wait/--no-wait", default=True, show_default=True,
              help="Wait for the reindex task to finish and poll progress.")
@click.option("--verify-only", is_flag=True, default=False,
              help="Only print doc counts; do not run the reindex (read-only).")
def main(
    pipeline_date: str,
    source_index: str,
    dest_index: str,
    version_type: str,
    conflicts: str,
    verify_field: str | None,
    wait: bool,
    verify_only: bool,
) -> None:
    es_client = get_superuser_es_client(pipeline_date)
    indices = [source_index, dest_index]

    click.echo("Before:")
    verify(es_client, indices=indices, verify_field=verify_field)

    if verify_only:
        return

    dest: dict = {"index": dest_index}
    if version_type != "internal":
        dest["version_type"] = version_type

    click.echo(
        f"\nReindexing {source_index} -> {dest_index} "
        f"(version_type={version_type}, conflicts={conflicts})..."
    )
    response = es_client.reindex(
        source={"index": source_index},
        dest=dest,
        conflicts=conflicts,
        wait_for_completion=False,
        request_timeout=120,
    )
    task_id = response["task"]
    click.echo(f"  task: {task_id}")

    if not wait:
        click.echo("  --no-wait: not polling. Re-run with --verify-only to check progress.")
        return

    while True:
        task = es_client.tasks.get(task_id=task_id)
        status = task["task"]["status"]
        click.echo(
            "  progress: "
            f"total={status['total']} created={status['created']} "
            f"updated={status['updated']} version_conflicts={status['version_conflicts']} "
            f"batches={status['batches']}"
        )
        if task["completed"]:
            failures = task["response"].get("failures", [])
            if failures:
                click.echo(f"  FAILURES ({len(failures)}): {failures[:3]} ...")
            break
        time.sleep(5)

    click.echo("\nAfter:")
    verify(es_client, indices=indices, verify_field=verify_field)


if __name__ == "__main__":
    main()
