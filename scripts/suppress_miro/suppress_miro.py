#!/usr/bin/env python3
import click
import httpx
import os
import sys

from miro_updates import (
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
    "--dry-run", help="Show what will happen, without actually doing it", is_flag=True
)
def suppress_miro(id_source, message, dry_run):
    """
    Suppresses a Miro image with a given ID.

    ID can be either a catalogue or a Miro identifier.
    """
    suppress = print_suppression_command if dry_run else suppress_image
    update = print_update_command if dry_run else update_miro_image_suppressions_doc

    # Run some pre-flight checks
    check_gh_cli_installed()

    for miro_id in valid_ids(id_source):
        suppress(miro_id=miro_id, message=message)

    # Run the update command
    update()


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


def print_suppression_command(miro_id, message):
    print(f"suppress_image(miro_id={miro_id}, message={message})")


def print_update_command():
    print("update_miro_image_suppressions_doc()")


if __name__ == "__main__":
    suppress_miro()
