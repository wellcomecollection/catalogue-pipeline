#!/bin/env python3

import requests
import click
import json


class ApiDiffer:
    """Performs a diff against the same call to both prod and stage works API,
    printing the results to stdout.
    """

    prod = "api.wellcomecollection.org"
    stage = "api-stage.wellcomecollection.org"

    def __init__(self, work_id=None, params=None, show_colour=True, verbose=False):
        suffix = f"/{work_id}" if work_id else ""
        self.path = f"/catalogue/v2/works{suffix}"
        self.params = params or {}
        self.show_colour = show_colour
        self.verbose = verbose

    def display_diff(self):
        click.echo(
            "================================================================================"
        )
        click.echo(f"Performing diff on {self.path} with params:")
        click.echo(self.params)
        click.echo(
            "================================================================================"
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
    """Performs a diff between 2 json-like Python objects, and prints changes
    between the two to stdout. It does this by flattening out any nesting within
    the objects so each nested item is referenced by a single top level key
    (represented as a variable length tuple containing string keys and array
    indices).
    """

    def __init__(self, obj_a, obj_b, name_a, name_b, show_colour=True, verbose=False):
        self.obj_a = dict(self.flatten(obj_a))
        self.obj_b = dict(self.flatten(obj_b))
        self.name_a = name_a
        self.name_b = name_b
        self.show_colour = show_colour
        self.verbose = verbose

    def display_diff(self):
        results = list(self.diff_results)
        if not self.verbose and not any(results):
            msg = "Unchanged between stage and prod"
            if self.show_colour:
                msg = click.style(msg, fg="green")
            click.echo(msg)
        else:
            self.display_results(results)

    def display_results(self, results):
        for (key, result) in zip(self.combined_keys, results):
            if result:
                a, b = map(self.format_value, result)
                msg = f"changed from {a} on {self.name_a} to {b} on {self.name_b}"
                col = "red"
            else:
                msg = f"unchanged between {self.name_a} and {self.name_b}"
                col = "green"
            if self.show_colour:
                msg = click.style(msg, fg=col)
            click.echo(f"{self.format_key(key)}: {msg}")

    def format_key(self, key):
        return ".".join(f"[{part}]" if isinstance(part, int) else part for part in key)

    def format_value(self, value):
        if isinstance(value, str):
            return f'"{value}"'
        if value is None:
            return "null"
        return str(value)

    @property
    def combined_keys(self):
        return sorted(set(self.obj_a.keys()) | set(self.obj_b.keys()))

    @property
    def diff_results(self):
        for key in self.combined_keys:
            a, b = self.obj_a.get(key), self.obj_b.get(key)
            yield None if a == b else (a, b)

    def flatten(self, obj):
        def flatten_tupled(obj):
            nested = [join_subitems(key, self.flatten(value)) for key, value in obj]
            return sum(nested, [])

        def join_subitems(key, subitems):
            return [((key, *subkey), value) for (subkey, value) in subitems]

        if isinstance(obj, list):
            return flatten_tupled(enumerate(obj))
        if isinstance(obj, dict):
            return flatten_tupled(obj.items())
        return [((), obj)]


@click.command()
@click.option(
    "--colour/--no-colour",
    default=True,
    help="Whether to display in terminal with colours or not",
)
@click.option(
    "--routes-file",
    default="routes.json",
    help="What routes file to use (default=routes.json)",
)
@click.option(
    "--verbose",
    default=False,
    help="Displays full diffs even when there are no changes.",
)
@click.option("--repeats", default=1, help="How many times to call each route")
def main(colour, routes_file, repeats, verbose):
    with open(routes_file) as f:
        routes = json.load(f)
    for route in routes:
        work_id, params = route.get("workId"), route.get("params")
        for _ in range(repeats):
            differ = ApiDiffer(work_id, params, show_colour=colour, verbose=verbose)
            differ.display_diff()


if __name__ == "__main__":
    main()
