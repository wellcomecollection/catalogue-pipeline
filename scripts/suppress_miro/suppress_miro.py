#!/usr/bin/env python3
"""
Always run the script with the --dry-run flag first to check that all required resources are available.
"""
import click
import httpx
import os
import sys

from miro_updates import (
    check_reindexer_listening,
    run_pre_suppression_checks,
    suppress_image,
    update_miro_image_suppressions_doc,
    is_valid_miro_id,
)


@click.command()
@click.option(
    "--id-source",
    help="newline-separated list of MIRO ids",
    type=click.File("r"),
    default=sys.stdin,
)
@click.option(
    "--message",
    help="Why the image was removed, a link to a Slack message, etc.",
    required=True,
)
@click.option(
    "--dry-run", help="Check that the suppression script can run successfully", is_flag=True
)


def suppress_miro(id_source, message, dry_run):
    """
    Suppresses a Miro image with a given ID.

    ID can be either a catalogue or a Miro identifier.
    """
    check_gh_cli_installed()

    if dry_run:
        check_reindexer_listening(dry_run=True)
        for miro_id in valid_ids(id_source):
            print("--------------------------------------------------------")
            print(f"Running checks for Miro ID: {miro_id}")
            run_pre_suppression_checks(miro_id)
            print("--------------------------------------------------------\n")
        print("When you run the suppression without the --dry-run flag, update_miro_image_suppressions_doc will be executed.")

    else:
        for miro_id in valid_ids(id_source):
            suppress_image(miro_id=miro_id, message=message)
        update_miro_image_suppressions_doc()


def check_gh_cli_installed():
    if os.system("gh --version > /dev/null") != 0:
        raise click.ClickException(
            "gh CLI not installed. Please install from https://cli.github.com/"
        )


def valid_ids(id_source):
    for single_id in id_source:
        single_id = single_id.strip()
        if is_valid_miro_id(single_id):
            yield single_id
        else:
            catalogue_response = httpx.get(
                f"https://api.wellcomecollection.org/catalogue/v2/works/{single_id}?include=identifiers"
            )
            try:
                response_data = catalogue_response.json()
                identifiers = response_data["identifiers"]
                miro_id = next(
                    i["value"]
                    for i in identifiers
                    if i["identifierType"]["id"] == "miro-image-number"
                )
                print(f"Miro identifier: {miro_id}")
            except Exception:
                raise click.ClickException(
                    f"{single_id} doesn't look like a Miro ID and isn't the identifier of a catalogue record containing a Miro ID"
                )


if __name__ == "__main__":
    suppress_miro()
