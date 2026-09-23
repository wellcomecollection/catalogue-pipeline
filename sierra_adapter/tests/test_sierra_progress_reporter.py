import json

import pytest

import sierra_progress_reporter as reporter


@pytest.fixture
def run_main(monkeypatch):
    """Run main() with AWS and Slack stubbed; bibs and items have gaps."""
    checked = []
    posts = []

    def fake_process_report(sess, *, bucket, resource_type):
        checked.append(resource_type)
        if resource_type in ("bibs", "items"):
            raise reporter.IncompleteReportError(resource_type)

    class FakeResponse:
        def raise_for_status(self):
            pass

    def fake_post(url, data, headers):
        posts.append(json.loads(data)["attachments"][0]["text"])
        return FakeResponse()

    monkeypatch.setenv("BUCKET", "bucket")
    monkeypatch.setattr(reporter.boto3, "Session", lambda: None)
    monkeypatch.setattr(reporter, "get_secret_string", lambda sess, **kw: "webhook")
    monkeypatch.setattr(reporter, "process_report", fake_process_report)
    monkeypatch.setattr(
        reporter, "prepare_missing_report", lambda sess, **kw: iter(["gap"])
    )
    monkeypatch.setattr(reporter.requests, "post", fake_post)

    def run():
        reporter.main()
        return checked, posts

    return run


def test_checks_every_resource_type_by_default(monkeypatch, run_main):
    monkeypatch.delenv("SKIPPED_RESOURCE_TYPES", raising=False)
    checked, posts = run_main()

    assert checked == ["bibs", "holdings", "items", "orders"]
    assert len(posts) == 1
    assert posts[0].startswith("There are gaps in the bibs/items data.")
    assert "paused" not in posts[0]


def test_empty_skip_list_checks_every_resource_type(monkeypatch, run_main):
    monkeypatch.setenv("SKIPPED_RESOURCE_TYPES", "")
    checked, _ = run_main()

    assert checked == ["bibs", "holdings", "items", "orders"]


def test_skipped_resource_types_are_not_checked(monkeypatch, run_main):
    monkeypatch.setenv("SKIPPED_RESOURCE_TYPES", "bibs")
    checked, posts = run_main()

    assert checked == ["holdings", "items", "orders"]
    assert len(posts) == 1
    assert posts[0].startswith("There are gaps in the items data.")
    assert "Not checked because updates are paused: bibs." in posts[0]


def test_no_alert_when_only_skipped_types_have_gaps(monkeypatch, run_main):
    monkeypatch.setenv("SKIPPED_RESOURCE_TYPES", "bibs, items")
    checked, posts = run_main()

    assert checked == ["holdings", "orders"]
    assert posts == []
