#!/bin/env python3

import requests
import click
import json


class ApiDiffer:

    prod = "api.wellcomecollection.org"
    stage = "api-stage.wellcomecollection.org"

    def __init__(self, work_id=None, params=None, show_colour=True):
        suffix = f"/{work_id}" if work_id else ""
        self.path = f"/catalogue/v2/works{suffix}"
        self.params = params or {}
        self.show_colour = show_colour

    def display_diff(self):
        click.echo(
            f"================================================================================"
        )
        click.echo(f"Performing diff on {self.path} with params:")
        click.echo(self.params)
        click.echo(
            f"================================================================================"
        )
        click.echo()
        click.echo("* Calling prod API")
        (prod_status, prod_json) = self.call_api(self.prod)
        click.echo("* Calling stage API")
        (stage_status, stage_json) = self.call_api(self.stage)
        if prod_status != stage_status:
            lines = [
                f"* Received {prod_status} on prod and {stage_status} on stage",
                "",
                "prod:",
                f"{json.dumps(prod_json, indent=2)}",
                "",
                "stage:",
                f"{json.dumps(stage_json, indent=2)}",
            ]
            msg = "\n".join(lines)
            if self.show_colour:
                msg = click.style(msg, fg="red")
            click.echo(msg)
        else:
            click.echo(f"* Recived {prod_status} status from both APIs")
            click.echo("* Generating diff")
            click.echo()
            differ = ObjDiffer(prod_json, stage_json, "prod", "stage", self.show_colour)
            differ.display_diff()
        click.echo()

    def call_api(self, api_base):
        url = f"https://{api_base}{self.path}"
        response = requests.get(url, params=self.params)
        return (response.status_code, response.json())


class ObjDiffer:
    def __init__(self, obj_a, obj_b, name_a, name_b, show_colour=True):
        self.obj_a = dict(self.flatten(obj_a))
        self.obj_b = dict(self.flatten(obj_b))
        self.name_a = name_a
        self.name_b = name_b
        self.show_colour = show_colour

    def display_diff(self):
        combined_keys = sorted(set(self.obj_a.keys()) | set(self.obj_b.keys()))
        for key in combined_keys:
            a, b = self.obj_a.get(key), self.obj_b.get(key)
            self.display_diff_line(key, a, b)

    def display_diff_line(self, key, a, b):
        def fmt(obj):
            if isinstance(obj, str):
                return f'"{obj}"'
            if obj is None:
                return "null"
            return str(obj)

        if a == b:
            msg = f"unchanged between {self.name_a} and {self.name_b}"
            col = "green"
        else:
            msg = f"changed from {fmt(a)} on {self.name_a} to {fmt(b)} on {self.name_b}"
            col = "red"
        if self.show_colour:
            msg = click.style(msg, fg=col)
        click.echo(f"{key}: {msg}")

    def flatten(self, obj):
        def flatten_tupled(obj):
            nested = [join_subitems(key, self.flatten(value)) for key, value in obj]
            flattened = sum(nested, [])
            return sorted(flattened)

        def join_subitems(key, subitems):
            return [(subkey.prepend(key), value) for (subkey, value) in subitems]

        if isinstance(obj, list):
            return flatten_tupled(enumerate(obj))
        if isinstance(obj, dict):
            return flatten_tupled(obj.items())
        return [(Key(), obj)]


class Key:
    def __init__(self, parts=None):
        self.parts = parts or ()

    def prepend(self, prefix):
        prefix = prefix.parts if isinstance(prefix, Key) else (prefix,)
        return Key(prefix + self.parts)

    def __str__(self):
        return ".".join(
            f"[{part}]" if isinstance(part, int) else part for part in self.parts
        )

    def __repr__(self):
        return str(self)

    def __lt__(self, other):
        return self.parts < other.parts

    def __hash__(self):
        return hash(self.parts)

    def __eq__(self, other):
        return self.parts == other.parts


def load_routes(filename):
    with open(filename) as f:
        return [(route.get("workId"), route.get("params")) for route in json.load(f)]


@click.command()
@click.option("--colour/--no-colour", default=True, help="Whether to display in terminal with colours or not")
@click.option("--routes-file", default="routes.json", help="What routes file to use (default=routes.json)")
@click.option("--repeats", default=1, help="How many times to call each route")
def main(colour, routes_file, repeats):
    for work_id, params in load_routes(routes_file):
        for _ in range(repeats):
            differ = ApiDiffer(work_id, params, show_colour=colour)
            differ.display_diff()


if __name__ == "__main__":
    main()
