
import boto3


class MetricReporter:
    def __init__(self, namespace: str) -> None:
        self.session = boto3.Session()
        self.client = self.session.client("cloudwatch")
        self.namespace = namespace

    def put_metric_data(
        self, metric_name: str, value: float, timestamp: str, dimensions: dict[str, str] | None = None
    ) -> None:
        dimensions = dimensions or {}
        
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
                    "Timestamp": timestamp
                }
            ],
        )
