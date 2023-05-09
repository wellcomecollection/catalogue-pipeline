#!/usr/bin/env python3
import click
import re
import httpx

from miro_updates import suppress_image, update_miro_image_suppressions_doc


miro_id_regex = re.compile("^[A-Z][0-9]{7}[A-Z]{0,4}[0-9]{0,2}$")


@click.command()
@click.argument("id")
@click.option(
    "--message",
    help="Why the image was removed, a link to a Slack message, etc.",
    required=True,
)
def suppress_miro(id, message):
    """
    Suppresses a Miro image with a given ID.

    ID can be either a catalogue or a Miro identifier.
    """

    if miro_id_regex.search(id):
        miro_id = id
    else:
        catalogue_response = httpx.get(
            f"https://api.wellcomecollection.org/catalogue/v2/works/{id}?include=identifiers"
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
                f"{id} doesn't look like a Miro ID and isn't the identifier of a catalogue record containing a Miro ID"
            )

    suppress_image(miro_id=miro_id, message=message)
    update_miro_image_suppressions_doc()


if __name__ == "__main__":
    suppress_miro()
