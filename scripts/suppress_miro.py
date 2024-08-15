#!/usr/bin/env python3
import click
import re
import httpx
import sys

from miro_updates import suppress_image, update_miro_image_suppressions_doc

miro_id_regex = re.compile("^[A-Z][0-9]{7}[A-Z]{0,4}[0-9]{0,2}$")


@click.command()
@click.option('--id_source',
              help='newline-separated list of MIRO ids',
              type=click.File('r'),
              default=sys.stdin)
@click.option(
    "--message",
    help="Why the image was removed, a link to a Slack message, etc.",
    required=True,
)
def suppress_miro(id_source, message):
    """
    Suppresses a Miro image with a given ID.

    ID can be either a catalogue or a Miro identifier.
    """
    for miro_id in valid_ids(id_source):
        suppress_image(miro_id={miro_id}, message={message})
    update_miro_image_suppressions_doc()


def valid_ids(id_source):
    for single_id in id_source:
        if miro_id_regex.search(single_id):
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
