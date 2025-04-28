def validate_fractional_change(
        modified_size: int, total_size: int, fractional_threshold: float, force_pass: bool = False
):
    """
    Check whether the modified fraction is within the `fractional_threshold`. 
    If not, raise an error unless `force_pass` is enabled.
    """
    fractional_diff = abs(modified_size) / total_size

    if fractional_diff > fractional_threshold:
        error_message = (
            f"Fractional change {fractional_diff:.2} "
            f"exceeds threshold {fractional_threshold:.2}!"
        )
        if not force_pass:
            raise ValueError(error_message)

        print(f"Force pass enabled: {error_message}, but continuing.")
    else:
        print(
            f"Fractional change {fractional_diff:.2} "
            f"({modified_size}/{total_size}) is within threshold "
            f"{fractional_threshold:.2}."
        )
