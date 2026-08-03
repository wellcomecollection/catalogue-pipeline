import json

import pytest

from eventbridge import (
    EVENTBRIDGE_REINDEX_TARGETS,
    send_eventbridge_reindex_event,
)


class FakeEventsClient:
    def __init__(self, failed_entry_count=0):
        self.failed_entry_count = failed_entry_count
        self.entries = None

    def put_events(self, Entries):
        self.entries = Entries
        return {"FailedEntryCount": self.failed_entry_count}


class FakeSession:
    def __init__(self, client):
        self._client = client

    def client(self, name):
        assert name == "events"
        return self._client


def send(target, failed_entry_count=0, **kwargs):
    client = FakeEventsClient(failed_entry_count)
    job_id = send_eventbridge_reindex_event(FakeSession(client), target, **kwargs)
    return client, job_id


@pytest.mark.parametrize("target", ["ebsco", "axiell"])
def test_sends_a_matching_event_for_each_adapter_source(target):
    client, job_id = send(target)

    (entry,) = client.entries
    assert entry["Source"] == "weco.pipeline.reindex"
    assert entry["DetailType"] == "weco.pipeline.reindex.requested"
    assert entry["EventBusName"] == "catalogue-pipeline-adapter-event-bus"

    # The trigger matches on reindex_targets and reads $.detail.job_id, which the
    # transformer requires. An event missing either cannot start a run.
    assert json.loads(entry["Detail"]) == {
        "reindex_targets": [target],
        "job_id": job_id,
    }


def test_uses_a_caller_supplied_job_id():
    client, job_id = send("axiell", job_id="reindex-axiell-manual")

    assert job_id == "reindex-axiell-manual"
    assert json.loads(client.entries[0]["Detail"])["job_id"] == "reindex-axiell-manual"


def test_generated_job_id_names_the_target():
    _, job_id = send("axiell")

    assert job_id.startswith("reindex-axiell-")


def test_rejects_an_unknown_target_without_sending():
    client = FakeEventsClient()

    with pytest.raises(ValueError, match="folio"):
        send_eventbridge_reindex_event(FakeSession(client), "folio")

    assert client.entries is None


def test_raises_when_the_event_is_not_accepted():
    with pytest.raises(RuntimeError):
        send("axiell", failed_entry_count=1)


def test_axiell_is_a_reindex_target():
    assert "axiell" in EVENTBRIDGE_REINDEX_TARGETS
