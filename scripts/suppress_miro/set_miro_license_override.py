#!/usr/bin/env python3
"""
Script to set a license override for a Miro image.
"""
import click
from miro_updates import set_license_override, is_valid_miro_id


@click.command()
@click.argument("miro_id")
@click.option(
    "--license-code",
    help="License code to set for the image",
    required=True,
)
@click.option(
    "--message",
    help="Reason for setting the license override, a link to a Slack message, etc.",
    required=True,
)
def set_miro_license_override(miro_id, license_code, message):
    """
    Set a license override for a Miro image.
    """
    if not is_valid_miro_id(miro_id):
        raise click.ClickException(f"'{miro_id}' is not a valid Miro ID")

    set_license_override(miro_id=miro_id, license_code=license_code, message=message)
    click.echo(f"License override set for {miro_id}: {license_code}")


if __name__ == "__main__":
    set_miro_license_override()
