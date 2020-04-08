import logging
import os
import sys

import logstash


def get_logstash_logger(name):
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)
    try:
        logstash_handler = logstash.LogstashHandler(
            os.environ["LOGSTASH_HOST"], int(os.environ["LOGSTASH_PORT"]), version=1
        )
        logger.addHandler(logstash_handler)
    except KeyError:
        stream_handler = logging.StreamHandler(sys.stdout)
        logger.addHandler(stream_handler)
    return logger
