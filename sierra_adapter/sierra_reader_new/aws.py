import json
import uuid


def get_secret_string(sess, **kwargs):
    secrets_client = sess.client("secretsmanager")

    return secrets_client.get_secret_value(**kwargs)["SecretString"]


def get_sns_batches(messages):
    """
    Given an iterable of SNS messages, group them into batches which
    are suitable for sending to the SNS PublishBatch API.

    There are two rules for batches:

    *   You can send up to 10 messages in a single batch
    *   A single batch can't exceed 256KB

    """
    this_batch = []

    for m in messages:
        this_message = {"Id": str(uuid.uuid4()), "Message": m}

        old_batch = this_batch
        new_batch = this_batch + [this_message]

        # If adding this message to the batch will push us over the 256KB
        # threshold, send all the other messages then start a new batch
        # with the latest message.
        if len(json.dumps(new_batch)) > 250 * 1024:

            # If we've just sent a batch of messages, old_batch may be
            # empty -- don't yield it unless there's something here.
            if old_batch:
                yield old_batch

            this_batch = [this_message]
            continue

        # If there are 10 messages in this batch, send the entire batch
        # and then start a new batch.
        elif len(new_batch) == 10:
            yield new_batch
            this_batch = []
            continue

        # Otherwise update the running batch, and pick up the next message.
        else:
            this_batch = new_batch

    # If there are any leftover records, send them when we're done.
    if this_batch:
        yield this_batch
