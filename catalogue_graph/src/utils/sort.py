import re


def natural_sort_key(key: str | None) -> list[int | str]:
    """
    A sort key which can be used to sort strings containing number sequences in a 'natural' way, where lower
    numbers come first. For example, 'A/10/B' < 'A/90/B', but 'A/10/B' > 'A/9/B'.
    """
    return [int(c) if c.isdigit() else c for c in re.split(r"(\d+)", key or "")]
