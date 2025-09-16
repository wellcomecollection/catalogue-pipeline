import json
import logging
import os

from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

HOOK_URL = os.environ["HOOK_URL"]
SLACK_CHANNEL = os.environ["SLACK_CHANNEL"]

logger = logging.getLogger()
logger.setLevel(logging.INFO)


def lambda_handler(event, context):
    logger.info("Event: " + str(event))
    message = json.loads(event["Records"][0]["Sns"]["Message"])
    logger.info("Message: " + str(message))

    alarm_name = message["AlarmName"]
    new_state = message["NewStateValue"]
    reason = message["NewStateReason"]

    slack_message = {
        "channel": SLACK_CHANNEL,
        "text": "%s state is now %s: %s" % (alarm_name, new_state, reason),
    }

    req = Request(HOOK_URL, json.dumps(slack_message).encode("utf-8"))
    try:
        response = urlopen(req)
        response.read()
        logger.info("Message posted to %s", slack_message["channel"])
    except HTTPError as e:
        logger.error("Request failed: %d %s", e.code, e.reason)
    except URLError as e:
        logger.error("Server connection failed: %s", e.reason)
