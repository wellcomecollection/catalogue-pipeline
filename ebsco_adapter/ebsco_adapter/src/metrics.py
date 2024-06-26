import boto3
import time


class ProcessMetrics:
    def __init__(self, process_name: str):
        self.process_name = process_name

    def __enter__(self):
        self.start_time = time.time()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        end_time = time.time()
        duration = end_time - self.start_time

        if exc_type is not None:
            self.put_metric("ProcessDurationFailure", duration)
        else:
            self.put_metric("ProcessDurationSuccess", duration)

    def put_metric(self, metric_name: str, value: float):
        print(f"Putting metric {metric_name} ({self.process_name}) with value {value}s")
        boto3.client('cloudwatch').put_metric_data(
            Namespace='ebsco_adapter',
            MetricData=[
                {
                    'MetricName': metric_name,
                    'Dimensions': [
                        {
                            'Name': 'process_name',
                            'Value': self.process_name
                        }
                    ],
                    'Value': value,
                    'Unit': 'Seconds'
                }
            ]
        )


