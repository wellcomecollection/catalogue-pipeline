import boto3
import os
import hashlib


class S3Store:
    def __init__(self, s3_bucket, s3_client=None):
        self.s3_bucket = s3_bucket
        if s3_client:
            self.s3_client = s3_client
        else:
            self.s3_client = boto3.client("s3")

    def list_files(self, s3_prefix):
        s3 = self.s3_client
        s3_object_listing = s3.list_objects_v2(Bucket=self.s3_bucket, Prefix=s3_prefix)

        s3_files = []
        if "Contents" in s3_object_listing:
            s3_files = [
                file["Key"].split("/")[-1] for file in s3_object_listing["Contents"]
            ]
        return s3_files

    def file_exists(self, s3_key, expected_sha256=None):
        s3 = self.s3_client
        try:
            response = s3.head_object(Bucket=self.s3_bucket, Key=s3_key)
            if expected_sha256:
                if response["Metadata"]["sha256"] != expected_sha256:
                    return False

            return True
        except Exception:
            return False

    def download_file(self, s3_key, local_directory):
        target_location = os.path.join(local_directory, s3_key.split("/")[-1])

        s3 = self.s3_client
        s3.download_file(self.s3_bucket, s3_key, target_location)

        return target_location

    def upload_file(self, s3_prefix, file):
        s3 = self.s3_client

        s3_key = os.path.join(s3_prefix, os.path.basename(file))
        s3.upload_file(file, self.s3_bucket, s3_key)

        return s3_key

    def create_file(self, s3_key, file_contents_as_bytes):
        # generate sha256 checksum of the file contents
        sha256_hash = hashlib.sha256(file_contents_as_bytes).hexdigest()

        s3 = self.s3_client

        synced = False
        if not self.file_exists(s3_key, expected_sha256=sha256_hash):
            s3.put_object(
                Bucket=self.s3_bucket,
                Key=s3_key,
                Body=file_contents_as_bytes,
                Metadata={"sha256": sha256_hash},
                ContentType="application/xml",
            )
            synced = True

        return {"s3_key": s3_key, "sha256": sha256_hash, "synced": synced}
