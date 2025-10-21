# Suppress Miro Images

This script suppresses Miro images in the Wellcome Collection catalogue pipeline. Suppression requests most often originate from take down requests, discussed in the [#takedown-requests Slack channel](https://wellcome.slack.com/archives/C0262TDBC58).

For more information on the take down request process, refer to the [takedown-requests documentation](https://github.com/wellcomecollection/private/blob/main/takedown-requests.md).

## Prerequisites

- [uv](https://github.com/astral-sh/uv) package manager
- [GitHub CLI](https://cli.github.com/) installed and authenticated

## Usage

Run the script in this folder using `uv`:

```bash
uv run suppress_miro.py [OPTIONS]
```

### Options

- `--id-source`: File containing newline-separated list of MIRO IDs (default: stdin). IDs can be either catalogue identifiers or Miro image numbers.
- `--message`: Required. Reason for suppression (e.g., a link to a Slack message explaining the take down request).
- `--dry-run`: Flag to show what would happen without actually performing the suppression.

### Examples

Suppress images from a file:

```bash
uv run suppress_miro.py --id-source ids.txt --message "Take down request: https://wellcome.slack.com/archives/C0262TDBC58/p1234567890"
```

Suppress images from stdin (pipe):

```bash
echo -e "M0000001\nM0000002" | uv run suppress_miro.py --message "Take down request: https://wellcome.slack.com/archives/C0262TDBC58/p1234567890"
```

Dry run to preview actions:

```bash
uv run suppress_miro.py --id-source ids.txt --message "Take down request: https://wellcome.slack.com/archives/C0262TDBC58/p1234567890" --dry-run
```

## Process

1. The script validates each provided ID (either Miro ID or catalogue work ID).
2. For each valid Miro ID, it suppresses the corresponding image.
3. The script triggers a workflow that opens a pull request to update the [miro-suppressions.md file](https://github.com/wellcomecollection/private/blob/main/miro-suppressions.md).
4. The PR must be reviewed and merged to complete the suppression process.

## Notes

- If a catalogue work ID is provided, the script will attempt to find the associated Miro ID.
- The script requires the GitHub CLI to be installed and authenticated for the PR creation workflow.
