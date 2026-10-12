from id_minter.models.identifier import SourceIdentifierKey


def check_predecessor_matches(
    sid: SourceIdentifierKey,
    pred: SourceIdentifierKey | None,
    found: dict[SourceIdentifierKey, str],
) -> None:
    """Raise if a registered source ID's registered predecessor has another canonical ID.

    An unregistered predecessor is allowed, so only a registry disagreement fails.
    """
    if (
        pred is not None
        and sid in found
        and pred in found
        and found[pred] != found[sid]
    ):
        raise ValueError(
            f"Predecessor mismatch for {sid[0]}/{sid[1]}/{sid[2]}: "
            f"registered as {found[sid]}, but predecessor "
            f"{pred[0]}/{pred[1]}/{pred[2]} is {found[pred]}"
        )
