import os


def find_notified_and_completed_flag(available_files, xml_s3_prefix, s3_store):
    available_files_dates = list(available_files.keys())
    dates_list = sorted(available_files_dates, reverse=True)

    print(f"Available uploads: {dates_list}")

    notified_completion_flag = "notified.flag"
    unpacking_completed_flag = "completed.flag"

    def _is_notified(date):
        notified_completion_flag_path = os.path.join(
            xml_s3_prefix, date, notified_completion_flag
        )
        return s3_store.file_exists(notified_completion_flag_path)

    def _is_completed(date):
        completed_flag_path = os.path.join(
            xml_s3_prefix, date, unpacking_completed_flag
        )
        return s3_store.file_exists(completed_flag_path)

    return [
        {
            "date": date,
            "notified_completed": _is_notified(date),
            "unpacking_completed": _is_completed(date),
        }
        for date in dates_list
    ]


def find_uploads_to_compare(available_files, xml_s3_prefix, s3_store):
    # Check if we've sent a notification for the date
    date_list_with_notified_flag = find_notified_and_completed_flag(
        available_files, xml_s3_prefix, s3_store
    )
    # The current date is the most recent if it has not been notified
    current_date = None
    if (
        len(date_list_with_notified_flag) > 0
        and not date_list_with_notified_flag[0]["notified_completed"]
    ):
        current_date = date_list_with_notified_flag[0]["date"]

    # The previous date is the next most recent if it has been notified
    previous_date = None
    if len(date_list_with_notified_flag) > 1:
        previous_date = next(
            (
                date["date"]
                for date in date_list_with_notified_flag[1:]
                if date["notified_completed"]
            ),
            None,
        )

    comparison_results = {"previous": previous_date, "current": current_date}

    print(
        f"Comparing uploads from {comparison_results['previous']} to {comparison_results['current']}"
    )

    return comparison_results


def find_updated_records(current_records, previous_records):
    for id, record in current_records.items():
        if id not in previous_records:
            yield id, record
        elif record["sha256"] != previous_records[id]["sha256"]:
            yield id, record


def find_deleted_records(current_records, previous_records):
    return (id for id in previous_records.keys() if id not in current_records)


def compare_uploads(
    available_files, marc_records_extractor, xml_s3_prefix, target_directory, s3_store
):
    assert len(available_files) > 0, "No files found to sync, stopping."
    dates_to_compare = find_uploads_to_compare(available_files, xml_s3_prefix, s3_store)

    if dates_to_compare["current"] is None:
        print("No upload found to process, stopping.")
        return None

    candidates_to_extract = {
        date: available_files[date]
        for date in dates_to_compare.values()
        if date is not None
    }
    records = marc_records_extractor(
        candidates_to_extract, xml_s3_prefix, target_directory, s3_store
    )

    assert (
        0 < len(records) <= 2
    ), f"Unexpected number of uploads to compare (got {len(records)}), stopping."
    assert (
        dates_to_compare["current"] in records
    ), "Current upload not found in extracted records, stopping."

    previous_available_count = (
        len(records[dates_to_compare["previous"]])
        if dates_to_compare["previous"]
        else 0
    )
    current_available_count = len(records[dates_to_compare["current"]])

    print(
        f"Previous upload has {previous_available_count} files, current upload has {current_available_count} files."
    )

    if len(records) == 1:
        print(
            f"No previous upload found to compare, notifying for {dates_to_compare['current']}."
        )
        print(
            f"Found {len(records[dates_to_compare['current']])} updated records to notify."
        )
        return {
            "notify_for_batch": dates_to_compare["current"],
            "updated": records[dates_to_compare["current"]],
            "deleted": None,
        }
    else:
        updated_records = dict(
            find_updated_records(
                records[dates_to_compare["current"]],
                records[dates_to_compare["previous"]],
            )
        )
        deleted_records = list(
            find_deleted_records(
                records[dates_to_compare["current"]],
                records[dates_to_compare["previous"]],
            )
        )
        print(
            f"Found {len(updated_records)} updated records and {len(deleted_records)} deleted records to notify."
        )
        return {
            "notify_for_batch": dates_to_compare["current"],
            "updated": updated_records,
            "deleted": deleted_records,
        }
