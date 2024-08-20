import json

EVENTBRIDGE_SOURCE = "weco.pipeline.reindex"
EVENTBRIDGE_REINDEX_TARGETS = ["ebsco"]

def send_eventbridge_reindex_event(session, reindex_target):
    """
    Send an AWS EventBridge event to trigger a re-index

    Not all re-indexes are triggered by EventBridge events. At present
    the only re-indexes triggered by EventBridge is the EBSCO adapter.
    """

    if reindex_target not in EVENTBRIDGE_REINDEX_TARGETS:
        raise ValueError(f"Invalid reindex target: {reindex_target}")

    print(f"Sending EventBridge event to reindex: {reindex_target}")
    response = session.client("events").put_events(
        Entries=[
            {
                "Source": EVENTBRIDGE_SOURCE,
                "DetailType": "Reindex triggered by start_reindex.py",
                "Detail": json.dumps({
                    "ReindexTargets": [reindex_target]
                }),
            }
        ]
    )

    if response["FailedEntryCount"] > 0:
        raise RuntimeError(f"Failed to send EventBridge event: {response}")


