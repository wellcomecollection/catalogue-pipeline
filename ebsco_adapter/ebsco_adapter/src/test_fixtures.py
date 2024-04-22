import os


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

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


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
