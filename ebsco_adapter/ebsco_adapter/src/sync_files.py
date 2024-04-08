import datetime
import os


# files are in the format ebz-s7451719-20240322-1.xml
# the second part is the customer id, the third part is an iso date, the fourth part is not important
def get_marc_file_details(filename):
    try:
        filename_parts = filename.split('-')
        assert len(filename_parts) == 4, f"Unexpected name parts for file {filename}! Skipping..."

        assert filename.endswith('.xml'), f"Invalid file type for file {filename}! Skipping..."
        assert filename.startswith(f"ebz-"), f"Unexpected file name for file {filename}! Skipping..."

        file_date = datetime.datetime.strptime(filename_parts[2], '%Y%m%d')
        return {
            "filename": filename,
            "date": file_date,
        }
    except AssertionError as e:
        print(e)
        return None
    except ValueError:
        print(f"Invalid date format for file {filename}! Skipping...")
        return None


def sync_files(temp_dir, s3_prefix, ebsco_ftp, s3_store):
    valid_suffixes = ['.xml']

    s3_files = [file for file in s3_store.list_files(s3_prefix) if file.endswith(tuple(valid_suffixes))]
    ftp_files = ebsco_ftp.list_files(valid_suffixes)

    print(f"Files found in FTP: {len(ftp_files)}")
    print(f"Files found in S3: {len(s3_files)}")

    files_to_download = list(set(ftp_files) - set(s3_files))
    print(f"Files to download: {len(files_to_download)}")

    uploaded_files = []
    if len(files_to_download) > 0:
        print(f"Downloading files to {temp_dir}")
        for file in files_to_download:
            with open(os.path.join(temp_dir, file), 'wb') as f:
                download_location = ebsco_ftp.download_file(file, temp_dir)

                file_details = get_marc_file_details(file)
                file_details["download_location"] = download_location
                file_details["upload_location"] = upload_location

                print(f"Uploading {file} to S3, location: {s3_prefix}, date: {file_details['date']}")
                upload_location = s3_store.upload_file(s3_prefix, download_location)

                uploaded_files.append(file_details)
    else:
        print("No files to download!")

    return uploaded_files
