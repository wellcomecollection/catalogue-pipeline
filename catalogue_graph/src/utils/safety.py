import structlog

logger = structlog.get_logger(__name__)

DEFAULT_THRESHOLD = 0.2


def validate_fractional_change(
    modified_size: int,
    total_size: int,
    fractional_threshold: float = DEFAULT_THRESHOLD,
    force_pass: bool = False,
) -> None:
    """
    Check whether the modified fraction is within the `fractional_threshold`.
    If not, raise an error unless `force_pass` is enabled.
    """
    if total_size == 0:
        logger.warning(
            "Cannot perform fractional change check due to a total size of 0"
        )
        return

    fractional_diff = abs(modified_size) / total_size

    if fractional_diff > fractional_threshold:
        error_message = (
            f"Fractional change {fractional_diff:.2} "
            f"exceeds threshold {fractional_threshold:.2}!"
        )
        if not force_pass:
            raise ValueError(error_message)

        logger.warning(
            "Force pass enabled, continuing despite threshold exceeded",
            fractional_change=f"{fractional_diff:.2}",
            threshold=f"{fractional_threshold:.2}",
        )
    else:
        logger.info(
            "Fractional change within threshold",
            fractional_change=f"{fractional_diff:.2}",
            modified_size=modified_size,
            total_size=total_size,
            threshold=f"{fractional_threshold:.2}",
        )
