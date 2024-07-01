import json
import os


class FakeSnsClient:
    def __init__(self):
        self.published_messages = []

    def test_reset(self):
        self.published_messages = []

    def test_get_published_messages(self):
        return self.published_messages

    def publish_batch(self, TopicArn, PublishBatchRequestEntries):
        success_response = {
            "Id": "string",
            "MessageId": "string",
            "MD5OfMessageBody": "string",
            "MD5OfMessageAttributes": "string",
            "SequenceNumber": "string",
        }

        for entry in PublishBatchRequestEntries:
            self.published_messages.append(json.loads(entry["Message"]))

        success_responses = [success_response for _ in PublishBatchRequestEntries]

        return {"Successful": success_responses, "Failed": []}


class FakeEbscoFtp:
    def __init__(self, files={}):
        self.files = files

    def __enter__(self):
        return self

    def list_files(self, valid_suffixes):
        return [
            file for file in self.files.keys() if file.endswith(tuple(valid_suffixes))
        ]

    def download_file(self, file, temp_dir):
        with open(os.path.join(temp_dir, file), "wb") as f:
            f.write(self.files[file])
        return os.path.join(temp_dir, file)

    def quit(self):
        pass

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


# This class is used to simulate the body of a streaming response from S3.
class FakeStreamingBody:
    def __init__(self, body):
        self.body = body

    def read(self):
        return self.body


class FakeS3Client:
    def __init__(self, objects=None):
        if objects is None:
            objects = {}
        self.objects = objects

    def list_objects_v2(self, Bucket, Prefix):
        return {
            "Contents": [
                {"Key": key} for key in self.objects.keys() if key.startswith(Prefix)
            ]
        }

    def head_object(self, Bucket, Key):
        return self.objects[Key]

    def download_file(self, Bucket, Key, target_location):
        with open(target_location, "wb") as f:
            f.write(self.objects[Key]["Body"])

    def get_object(self, Bucket, Key):
        streaming_body = FakeStreamingBody(self.objects[Key]["Body"])
        return {"Body": streaming_body}

    def upload_file(self, file, Bucket, Key):
        with open(file, "rb") as f:
            file_contents = f.read()

        self.objects[Key] = {
            "Body": file_contents,
        }

    def put_object(self, Bucket, Key, Body, Metadata, ContentType):
        self.objects[Key] = {
            "Body": Body,
            "Metadata": Metadata,
            "ContentType": ContentType,
        }
