from datetime import datetime, timedelta

import boto3


class MetricReporter:
    def __init__(self, namespace: str) -> None:
        self.session = boto3.Session()
        self.client = self.session.client("cloudwatch")
        self.namespace = namespace

    def put_metric_data(
        self,
        metric_name: str,
        value: float,
        timestamp: datetime,
        dimensions: dict[str, str] | None = None,
    ) -> None:
        dimensions = dimensions or {}

        # CloudWatch does not support sending metrics older than 2 weeks
        two_weeks_ago = datetime.now() - timedelta(weeks=2)
        if two_weeks_ago > timestamp:
            print(
                "Did not publish CloudWatch metrics. Provided timestamp is too far in the past."
            )
            return

        self.client.put_metric_data(
            Namespace=self.namespace,
            MetricData=[
                {
                    "MetricName": metric_name,
                    "Value": value,
                    "Unit": "Count",
                    "Dimensions": [
                        {"Name": k, "Value": v} for k, v in dimensions.items()
                    ],
                    "Timestamp": timestamp,
                }
            ],
        )
