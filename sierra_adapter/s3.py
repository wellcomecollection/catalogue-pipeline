import os

import tqdm


def download_object_from_s3(session, *, bucket, key, version_id=None, filename):
    """
    Download an object from S3 with a progress bar using tqdm.

    See https://alexwlchan.net/2021/02/s3-progress-bars/
    """
    s3 = session.client("s3")

    kwargs = {"Bucket": bucket, "Key": key}

    if version_id is not None:
        kwargs["VersionId"] = version_id

    object_size = s3.head_object(**kwargs)["ContentLength"]

    if version_id is not None:
        ExtraArgs = {"VersionId": version_id}
    else:
        ExtraArgs = None

    with tqdm.tqdm(total=object_size, unit="B", unit_scale=True, desc=filename) as pbar:
        s3.download_file(
            Bucket=bucket,
            Key=key,
            ExtraArgs=ExtraArgs,
            Filename=filename,
            Callback=lambda bytes_transferred: pbar.update(bytes_transferred),
        )


def upload_file_to_s3(session, *, bucket, key, filename):
    """
    Upload a file to S3 with a progress bar using tqdm.

    See https://alexwlchan.net/2021/02/s3-progress-bars/
    """
    file_size = os.stat(filename).st_size

    s3 = session.client("s3")

    with tqdm.tqdm(total=file_size, unit="B", unit_scale=True, desc=filename) as pbar:
        s3.upload_file(
            Filename=filename,
            Bucket=bucket,
            Key=key,
            Callback=lambda bytes_transferred: pbar.update(bytes_transferred),
        )
