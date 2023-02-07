#!/usr/bin/env python3
"""
This prints a graph of all the Sierra records that we've linked together.

e.g. which items are attached to this bib

It's helpful for debugging the Sierra adapter.
"""

import json
import os
import sys

import graphviz

from get_sierra_transformable_record import (
    get_user_input,
    get_bnumber_from_user_input,
    get_transformable,
)


def add_check_digit(id_str, *, prefix):
    # from https://github.com/alexwlchan/add_sierra_check_digit/blob/main/add_check_digit.py
    total = sum(i * int(digit) for i, digit in enumerate(reversed(id_str), start=2))

    remainder = total % 11

    if remainder == 10:
        return prefix + id_str + "x"
    else:
        return prefix + id_str + str(remainder)


if __name__ == "__main__":
    user_input = get_user_input(sys.argv)
    bnumber = get_bnumber_from_user_input(user_input)

    dot = graphviz.Digraph()

    seen_bnumbers = set()
    remaining_bnumbers = set([bnumber])

    b = dot.node(
        add_check_digit(bnumber, prefix="b"), style="filled", color="lightgrey"
    )

    while remaining_bnumbers:
        bnumber = remaining_bnumbers.pop()
        transformable = get_transformable(bnumber)

        for (recordType, prefix) in [
            ("itemRecords", "i"),
            ("holdingRecords", "c"),
            ("orderRecords", "o"),
        ]:
            for record in transformable.get(recordType, {}).values():
                for linked_bnumber in json.loads(record["data"])["bibIds"]:
                    dot.node(add_check_digit(linked_bnumber, prefix="b"))
                    dot.node(add_check_digit(record["id"], prefix=prefix))
                    dot.edge(
                        add_check_digit(record["id"], prefix=prefix),
                        add_check_digit(linked_bnumber, prefix="b"),
                    )

                    if (
                        linked_bnumber != bnumber
                        and linked_bnumber not in seen_bnumbers
                    ):
                        remaining_bnumbers.add(linked_bnumber)

        seen_bnumbers.add(bnumber)

    graphs_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_graphs")
    out_path = os.path.join(graphs_dir, add_check_digit(bnumber, prefix="b"))

    dot.render(out_path)
    print(os.path.relpath(f"{out_path}.pdf", "."))
