#!/usr/bin/env python3
import click

from miro_updates import unsuppress_image, is_valid_miro_id


@click.command()
@click.argument("id")
@click.option(
    "--origin",
    help="URL of the image",
    required=True,
)
@click.option(
    "--message",
    help="Why the image was reinstated, a link to a Slack message, etc.",
    required=True,
)
def unsuppress_miro(id, origin, message):
    """
    Reinstates a previously suppressed Miro image with a given ID and origin

    ID is a MIRO identifier
    origin is the URL of the image it corresponds to.

    Prerequisites:
    - You have a MIRO id you wish to reinstate.
    - Find the image in the Storage Service bucket
    - Configure the pipeline to listen to the reindexer

    Usage:
    - provide the MIRO id as --id
    - provide the https://s3... URL for the image as --origin
    - give a reason or link in --message

    thus:

        python unsuppress_miro.py --id L0099099 --origin https://s3-.../L0099099.JP2 --message "because I say so"

    This may fail with a message
        "Delivery channels are required when updating an existing Asset via PUT"

    This indicates that the image in question is already on DLCS (though it may be in an error state).
    If you are confident that it is not working, and you wish it to be, suppress it
    (specifically, this is in order remove it from DLCS) and try again.
    """
    id = id.strip()
    if is_valid_miro_id(id):
        miro_id = id
    else:
        raise click.ClickException(
            f"{id} doesn't look like a Miro ID and isn't the identifier of a catalogue record containing a Miro ID"
        )

    unsuppress_image(miro_id=miro_id, origin=origin, message=message)


if __name__ == "__main__":
    unsuppress_miro()
