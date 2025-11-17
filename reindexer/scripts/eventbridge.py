import json

EVENTBUS_NAME = "catalogue-pipeline-adapter-event-bus"
EVENTBRIDGE_SOURCE = "weco.pipeline.reindex"
EVENTBRIDGE_REINDEX_TARGETS = ["ebsco"]
EVENT_REQUESTED_DETAIL_TYPE = "weco.pipeline.reindex.requested"


def send_eventbridge_reindex_event(session, reindex_target):
    """
    Send an AWS EventBridge event to trigger a re-index

    Not all re-indexes are triggered by EventBridge events. At present
    the only re-indexes triggered by EventBridge is the EBSCO adapter.
    """

    if reindex_target not in EVENTBRIDGE_REINDEX_TARGETS:
        raise ValueError(f"Invalid reindex target: {reindex_target}")

    response = session.client("events").put_events(
        Entries=[
            {
                "Source": EVENTBRIDGE_SOURCE,
                "DetailType": EVENT_REQUESTED_DETAIL_TYPE,
                "Detail": json.dumps({"reindex_targets": [reindex_target]}),
                "EventBusName": EVENTBUS_NAME,
            }
        ]
    )

    if response["FailedEntryCount"] > 0:
        raise RuntimeError(f"Failed to send EventBridge event: {response}")
