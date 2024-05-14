# -*- encoding: utf-8 -*-

import datetime as dt
import os
from unittest import mock

from window_generator import build_window, main


pytest_plugins = ["aws_test_helpers"]


class patched_datetime(dt.datetime):
    @classmethod
    def now(cls, tz=None):
        return dt.datetime(2011, 6, 21, 0, 0, 0, 0, tzinfo=tz)


@mock.patch("datetime.datetime", patched_datetime)
def test_build_window():
    assert build_window(minutes=15) == {
        "start": "2011-06-20T23:45:00+00:00",
        "end": "2011-06-21T00:00:00+00:00",
    }


def test_end_to_end(test_topic_arn, get_test_topic_messages, mock_sns_client):
    env = {"WINDOW_LENGTH_MINUTES": "25", "TOPIC_ARN": test_topic_arn}
    os.environ["WINDOW_LENGTH_MINUTES"] = "25"

    with mock.patch.dict(os.environ, env), mock.patch(
        "datetime.datetime", patched_datetime
    ):
        # This Lambda doesn't read anything from its event or context
        main(sns_client=mock_sns_client)

    messages = list(get_test_topic_messages())
    assert len(messages) == 1
    assert messages[0] == {
        "start": "2011-06-20T23:35:00+00:00",
        "end": "2011-06-21T00:00:00+00:00",
    }
