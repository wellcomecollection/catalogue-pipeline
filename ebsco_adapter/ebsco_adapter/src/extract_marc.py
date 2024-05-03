import os
import json

import pymarc


def split_marc_records(file_path):
    with open(file_path, "rb") as marc_file:
        reader = pymarc.marcxml.parse_xml_to_array(marc_file)
        records = {}
        for record in reader:
            control_field = record["001"]

            if not control_field:
                raise ValueError("Control field not found in record")

            records[control_field.data] = pymarc.marcxml.record_to_xml(record)

        return records


def extract_marc_records(
    available_files, xml_s3_prefix, target_directory, s3_store, batch_size=100
):
    unpacked_files = {}
    for batch_name, file in available_files.items():
        batch_completion_flag = "completed.flag"
        batch_completion_flag_path = os.path.join(
            xml_s3_prefix, batch_name, batch_completion_flag
        )
        batch_completed = s3_store.file_exists(batch_completion_flag_path)

        if not batch_completed:
            if "download_location" not in file:
                download_location = s3_store.download_file(
                    file["upload_location"], target_directory
                )
            else:
                download_location = file["download_location"]

            print(f"Unpacking {batch_name}")

            marc_records = split_marc_records(download_location)
            print(f"Extracted {len(marc_records)} records, saving.")

            batches = [
                dict(list(marc_records.items())[i : i + batch_size])
                for i in range(0, len(marc_records), batch_size)
            ]

            files_uploaded = {}
            for i, batch in enumerate(batches):
                print(f"Uploading batch {i + 1} of {len(batches)} ...")
                upload_prefix = os.path.join(xml_s3_prefix, batch_name)

                already_uploaded = []
                for control_number, record in batch.items():
                    s3_key = os.path.join(upload_prefix, f"{control_number}.xml")
                    uploaded_file = s3_store.create_file(
                        s3_key, record, "application/xml"
                    )
                    files_uploaded[control_number] = {
                        "s3_key": s3_key,
                        "sha256": uploaded_file["sha256"],
                    }
                    if not uploaded_file["synced"]:
                        already_uploaded.append(control_number)
                print(
                    f"Uploaded {len(batch) - len(already_uploaded)} records, {len(already_uploaded)} already uploaded."
                )

            json_encoded_files_uploaded = json.dumps(files_uploaded)
            s3_store.create_file(
                batch_completion_flag_path,
                json_encoded_files_uploaded.encode("utf-8"),
                "application/json",
            )

            unpacked_files[batch_name] = files_uploaded
        else:
            print(f"Batch {batch_name} already unpacked.")
            downloaded_flag = s3_store.download_file(
                batch_completion_flag_path, target_directory
            )
            with open(downloaded_flag, "r") as f:
                files_uploaded = json.load(f)
            unpacked_files[batch_name] = files_uploaded

    return unpacked_files
