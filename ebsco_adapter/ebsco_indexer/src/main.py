from datetime import datetime

# This is required to ensure that the datetime is in the correct format
# for the update_notifier function, Python's datetime.isoformat() does not
# include the 'Z' at the end of the string for older versions of Python.
def _get_iso8601_invoked_at():
    invoked_at = datetime.utcnow().isoformat()
    if invoked_at[-1] != "Z":
        invoked_at += "Z"
    return invoked_at


def lambda_handler(event, context):
    invoked_at = _get_iso8601_invoked_at()
    if "invoked_at" in event:
        invoked_at = event["invoked_at"]

    print(f"Starting lambda_handler @ {invoked_at}, got event: {event}")

    return None


if __name__ == "__main__":
    event = {}

    lambda_handler(event, None)
