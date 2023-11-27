import boto3
import re
import os

RE_BNUMBER_METSFILE = re.compile("^b[0-9]{7}[0-9x].xml$")
RE_BNUMBER_MANIFESTATION = re.compile("^b[0-9]{7}[0-9x]_\w+.xml$")
RE_BD_METSFILE = re.compile("^METS\..+\.xml$")
STORAGE_ROLE = "arn:aws:iam::975596993436:role/storage-read_only"


def aws_resource(name, *, role_arn):
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


def main():
    s3_resource = aws_resource("s3", role_arn=STORAGE_ROLE)
    bd_bucket = s3_resource.Bucket("wellcomecollection-storage")
    i = 0
    for s3_object in bd_bucket.objects.filter(
        Prefix="digitised/", Marker="digitised/b10283651/v1/manifest-sha256.txt"
    ):
        bare_name = s3_object.key.rpartition("/")[2]
        if RE_BNUMBER_METSFILE.match(bare_name):
            i += 1
            bd_bucket.download_file(s3_object.key, os.path.join("data", bare_name))
            if i % 1000 == 0:
                print(i)


if __name__ == "__main__":
    main()
