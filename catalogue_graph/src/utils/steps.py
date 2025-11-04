from datetime import datetime


def create_job_id() -> str:
    """Generate a job_id based on the current time using an iso8601 format like 20210701T1300"""
    return datetime.now().strftime("%Y%m%dT%H%M")
