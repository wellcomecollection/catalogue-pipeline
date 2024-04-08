import boto3
import os

class S3Store:
    def __init__(self, s3_bucket, s3_client=None):
        self.s3_bucket = s3_bucket
        if s3_client:
            self.s3_client = s3_client
        else:
            self.s3_client = boto3.client('s3')

    def list_files(self, s3_prefix):
        s3 = boto3.client('s3')
        s3_object_listing = s3.list_objects_v2(Bucket=self.s3_bucket, Prefix=s3_prefix)

        s3_files = []
        if 'Contents' in s3_object_listing:
            s3_files = [file['Key'].split('/')[-1] for file in s3_object_listing['Contents']]
        return s3_files

    def upload_file(self, s3_prefix, file):
        s3 = boto3.client('s3')

        s3_key = os.path.join(s3_prefix, os.path.basename(file))
        s3.upload_file(file, self.s3_bucket, s3_key)

        return s3_key
