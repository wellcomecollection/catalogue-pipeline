#!/usr/bin/env python3

"""
Finds documents that don't appear in _both_ of 2 given pipeline indices.

See the section of the README "fixing leaks" for more info.
"""

import click
from elasticsearch.helpers import scan
from tqdm import tqdm

from get_reindex_status import get_pipeline_storage_es_client, count_documents_in_index


@click.command()
@click.argument("reindex_date")
@click.option("--from", "from_index", required=True)
@click.option("--to", "to_index", required=True)
@click.argument("out_file_path")
def main(reindex_date, from_index, to_index, out_file_path):
    es = get_pipeline_storage_es_client(reindex_date)
    indices = [f"{from_index}-{reindex_date}", f"{to_index}-{reindex_date}"]
    counts = [count_documents_in_index(es, index_name=index) for index in indices]

    unmatched_ids = {}
    for hit in tqdm(
        scan(es, scroll="15m", _source=False, index=indices), total=sum(counts)
    ):
        hit_id = hit["_id"]
        hit_index = hit["_index"]
        # Given that we are scrolling both indices, if we don't see an ID
        # twice then it only exists in one index.
        if hit_id in unmatched_ids:
            del unmatched_ids[hit_id]
        else:
            unmatched_ids[hit_id] = hit_index

    print(f"Found {len(unmatched_ids)} documents that do not appear in both indices.")

    with open(out_file_path, "w") as out_file:
        for id, index in unmatched_ids.items():
            if index != indices[0]:
                print(
                    f"{id} is in {indices[1]} but not {indices[0]}, how did this happen?"
                )
            out_file.write(id)
            out_file.write("\n")

    print(f"Wrote unmatched IDs to {out_file_path}")


if __name__ == "__main__":
    main()
