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


def get_batch_name(file):
    return file["date"].strftime('%Y-%m-%d')


def list_files(s3_prefix, s3_store):
    valid_suffixes = ['.xml']
    s3_files = [file for file in s3_store.list_files(s3_prefix) if file.endswith(tuple(valid_suffixes))]

    available_files = []
    for file in s3_files:
        upload_location = os.path.join(s3_prefix, os.path.basename(file))
        file_details = get_marc_file_details(file)
        file_details["upload_location"] = upload_location
        file_details["batch_name"] = get_batch_name(file_details)
        available_files.append(file_details)

    return available_files


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

                print(f"Uploading {file} to S3, location: {s3_prefix}, date: {file_details['date']}")
                upload_location = s3_store.upload_file(s3_prefix, download_location)

                file_details["download_location"] = download_location
                file_details["upload_location"] = upload_location
                file_details["batch_name"] = get_batch_name(file_details)

            uploaded_files.append(file_details)
    else:
        print("No files to download!")

    return uploaded_files


# Returns a dictionary of files with the filename as the key, if the file has just been
# uploaded, the download location will be included in the dictionary.
def sync_and_list_files(temp_dir, s3_prefix, ebsco_ftp, s3_store):
    uploaded_files_list = sync_files(temp_dir, s3_prefix, ebsco_ftp, s3_store)
    available_files_list = list_files(s3_prefix, s3_store)

    uploaded_files = {}
    for file in uploaded_files_list:
        uploaded_files[file['batch_name']] = file

    file_list = {}
    for file in available_files_list:
        if file['batch_name'] in uploaded_files:
            file['download_location'] = uploaded_files[file['batch_name']]['download_location']
        file_list[file['batch_name']] = file

    return file_list
