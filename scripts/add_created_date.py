import boto3
import concurrent.futures
from yaspin import yaspin
from boto3.dynamodb.conditions import Key
import json
import itertools
import maya
import math

platform_dev_role = "arn:aws:iam::760097843905:role/platform-developer"
adapter_table_name = "mets-adapter-store"

def aws_resource(name, role_arn):
    role = boto3.client("sts").assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = role["Credentials"]
    return boto3.resource(
        name,
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


class StorageManifestScanner:
    storage_role = "arn:aws:iam::975596993436:role/storage-read_only"
    adapter_role = "arn:aws:iam::760097843905:role/platform-read_only"
    storage_table_name = "vhs-storage-manifests-2020-07-24"
    adapter_table_name = "mets-adapter-store"

    def __init__(self, total_segments, max_scans_in_parallel):
        self.s3 = aws_resource("s3", role_arn=self.storage_role)
        dynamodb_storage_role = aws_resource("dynamodb", self.storage_role)
        dynamodb_adapter_role = aws_resource("dynamodb", self.adapter_role)
        self.total_segments = total_segments
        self.max_scans_in_parallel = max_scans_in_parallel
        self.adapter_table = dynamodb_adapter_role.Table(self.adapter_table_name)
        self.storage_table = dynamodb_storage_role.Table(self.storage_table_name)

    def scan(self):
        tasks_to_do = [
            {"Segment": segment, "TotalSegments": self.total_segments}
            for segment in range(self.total_segments)
        ]

        scans_to_run = iter(tasks_to_do)
        with concurrent.futures.ThreadPoolExecutor() as executor:
            futures = {
                executor.submit(self.adapter_table.scan, **scan_params): scan_params
                for scan_params in itertools.islice(scans_to_run, self.max_scans_in_parallel)
            }
            while futures:
                # Wait for the first future to complete.
                done, _ = concurrent.futures.wait(
                    futures, return_when=concurrent.futures.FIRST_COMPLETED
                )

                for fut in done:
                    for item in fut.result()["Items"]:
                        id = item["id"]
                        if 'createdDate' not in item['payload'].keys():
                            stored = self.storage_table.query(
                                                     KeyConditionExpression=Key('id').eq(f"digitised/{id}"),
                                                     Limit = 1,
                                                     ScanIndexForward = False)["Items"][0]
                            bucket = stored["payload"]["bucket"]
                            key = stored["payload"]["key"]
                            bag_manifest = json.loads(self.s3.Object(bucket, key).get()["Body"].read().decode("utf-8"))
                            yield id, bag_manifest["createdDate"]

                    scan_params = futures.pop(fut)

                    try:
                        scan_params["ExclusiveStartKey"] = fut.result()["LastEvaluatedKey"]
                    except KeyError:
                        break
                    tasks_to_do.append(scan_params)

                for scan_params in itertools.islice(scans_to_run, len(done)):
                    futures[executor.submit(self.adapter_table.scan, **scan_params)] = scan_params


def update(table, batch_writer, id, created_date):
    item = table.get_item(
        Key={"id": id}
    )["Item"]
    created_date_milliseconds = math.floor(maya.parse(created_date).datetime().timestamp() * 1000)
    item['payload']['createdDate'] = created_date_milliseconds
    batch_writer.put_item(Item=item)


def main():
    scanner = StorageManifestScanner(total_segments=400, max_scans_in_parallel=50)
    dynamodb = aws_resource("dynamodb", platform_dev_role)
    table = dynamodb.Table(adapter_table_name)
    with table.batch_writer() as batch_writer:
        with yaspin():
            for id, created_date in scanner.scan():
                update(table, batch_writer, id, created_date)



if __name__ == "__main__":
    main()
