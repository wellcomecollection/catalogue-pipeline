import os


def update_notifier(
    updates, notify_for_batch, s3_store, s3_bucket, xml_s3_prefix, sns_publisher
):
    update_messages = []
    deleted_messages = []

    for update_id, update in dict(updates["updated"]).items():
        update_messages.append(
            {
                "type": "updated",
                "id": update_id,
                "s3_location": f"s3://{s3_bucket}/{update['s3_key']}",
                "sha256": update["sha256"],
            }
        )

    for delete_id in updates["deleted"]:
        deleted_messages.append({"type": "deleted", "id": delete_id})

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
    s3_store.create_file(notified_completion_flag_path, b"")
