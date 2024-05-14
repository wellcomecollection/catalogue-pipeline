import os


def update_notifier(
    updates, notify_for_batch, s3_store, s3_bucket, xml_s3_prefix, sns_publisher
):
    update_messages = []
    deleted_messages = []

    version = int("".join(filter(str.isdigit, notify_for_batch)))

    if updates["updated"] is not None:
        for update_id, update in dict(updates["updated"]).items():
            update_messages.append(
                {
                    "id": update_id,
                    "location": {
                        "bucket": s3_bucket,
                        "key": update["s3_key"],
                    },
                    "version": version,
                    "deleted": False,
                    "sha256": update["sha256"],
                }
            )

    if updates["deleted"] is not None:
        for delete_id in updates["deleted"]:
            deleted_messages.append(
                {
                    "id": delete_id,
                    "location": None,
                    "version": version,
                    "deleted": True,
                    "sha256": None,
                }
            )

    print(
        f"Sending {len(update_messages)} update messages and {len(deleted_messages)} delete messages."
    )
    sns_publisher.publish(update_messages)
    print(f"Sent {len(update_messages)} update messages.")
    sns_publisher.publish(deleted_messages)
    print(f"Sent {len(deleted_messages)} delete messages.")

    # TODO: Extract notifiers to a separate function (and flags in general)
    notified_completion_flag = "notified.flag"
    notified_completion_flag_path = os.path.join(
        xml_s3_prefix, notify_for_batch, notified_completion_flag
    )
    s3_store.create_file(notified_completion_flag_path, b"", "application/txt")
