#!/usr/bin/env python3
"""
Script to remove a license override for a Miro image.
"""
import click
from miro_updates import remove_license_override, is_valid_miro_id


@click.command()
@click.argument("miro_id")
@click.option(
    "--message",
    help="Reason for removing the license override, a link to a Slack message, etc.",
    required=True,
)
def remove_miro_license_override(miro_id, message):
    """
    Remove a license override for a Miro image.
    """
    if not is_valid_miro_id(miro_id):
        raise click.ClickException(f"'{miro_id}' is not a valid Miro ID")

    remove_license_override(miro_id=miro_id, message=message)
    click.echo(f"License override removed for {miro_id}")


if __name__ == "__main__":
    remove_miro_license_override()
