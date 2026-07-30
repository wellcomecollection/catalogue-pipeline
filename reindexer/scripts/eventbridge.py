import json
from datetime import datetime

EVENTBUS_NAME = "catalogue-pipeline-adapter-event-bus"
EVENTBRIDGE_SOURCE = "weco.pipeline.reindex"
EVENTBRIDGE_REINDEX_TARGETS = ["ebsco", "axiell"]
EVENT_REQUESTED_DETAIL_TYPE = "weco.pipeline.reindex.requested"


def create_job_id(reindex_target):
    """Match the adapters' job id format, prefixed so a reindex is recognisable."""
    return f"reindex-{reindex_target}-{datetime.now().strftime('%Y%m%dT%H%M')}"


def send_eventbridge_reindex_event(session, reindex_target, job_id=None):
    """
    Send an AWS EventBridge event to trigger a re-index

    Not all re-indexes are triggered by EventBridge events. The adapter
    sources (EBSCO, Axiell) use this path; the rest go through the reindexer
    itself.

    Returns the job_id the event carried, which is how the resulting
    transformer run is traced.
    """

    if reindex_target not in EVENTBRIDGE_REINDEX_TARGETS:
        raise ValueError(f"Invalid reindex target: {reindex_target}")

    job_id = job_id or create_job_id(reindex_target)

    response = session.client("events").put_events(
        Entries=[
            {
                "Source": EVENTBRIDGE_SOURCE,
                "DetailType": EVENT_REQUESTED_DETAIL_TYPE,
                # job_id is required: the trigger reads $.detail.job_id, and the
                # transformer event model rejects a run without one.
                "Detail": json.dumps(
                    {"reindex_targets": [reindex_target], "job_id": job_id}
                ),
                "EventBusName": EVENTBUS_NAME,
            }
        ]
    )

    if response["FailedEntryCount"] > 0:
        raise RuntimeError(f"Failed to send EventBridge event: {response}")

    return job_id
