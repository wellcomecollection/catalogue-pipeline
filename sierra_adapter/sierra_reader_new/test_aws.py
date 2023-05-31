import secrets
import unittest

from aws import get_sns_batches


class TestGetSnsBatches(unittest.TestCase):
    def assertGetsCorrectBatches(self, *, messages, expectedBatches):
        actualBatches = list(get_sns_batches(messages))

        self.assertEqual(len(actualBatches), len(expectedBatches))

        for actual, expected in zip(actualBatches, expectedBatches):
            self.assertTrue(all(entry.keys() == {"Id", "Message"} for entry in actual))
            self.assertEqual([entry["Message"] for entry in actual], expected)

    def test_it_handles_an_empty_list(self):
        self.assertGetsCorrectBatches(messages=[], expectedBatches=[])

    def test_it_splits_messages_into_batches_of_ten(self):
        self.assertGetsCorrectBatches(
            messages=[f"message-{i}" for i in range(20)],
            expectedBatches=[
                [f"message-{i}" for i in range(10)],
                [f"message-{i}" for i in range(10, 20)],
            ],
        )

    def test_it_splits_messages_which_exceed_256kb(self):
        messages = [secrets.token_hex(252 * 1024) for _ in range(5)]

        self.assertGetsCorrectBatches(
            messages=messages, expectedBatches=[[m] for m in messages]
        )


if __name__ == "__main__":
    unittest.main()
