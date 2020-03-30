import logging
import os

import logstash


def get_logstash_logger(name):
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)
    logstash_handler = logstash.LogstashHandler(
        os.environ['LOGSTASH_HOST'], int(os.environ['LOGSTASH_PORT']),
        version=1
    )
    logger.addHandler(logstash_handler)
    return logger
