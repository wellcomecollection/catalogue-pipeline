import pytest
from itertools import chain
from id_minter import identifiers


# Tests as documentation for the properties of the generated ids
# Because id generation is random, testing the output of a single call to generate_id() is not very useful,
# as any success might just be due to luck.  However, these tests document the nature of the expected output
# as a form of double-entry bookkeeping.

def assert_first_character_valid(generated_id: str):
    assert generated_id[0] in "abcdefghjkmnpqrstuvwxyz", \
        f"first character of id {generated_id} is not an acceptable letter"
    return True


def assert_all_characters_valid(generated_id: str):
    for c in generated_id:
        # check that no forbidden characters are present
        # not exactly necessary, as the next assertion would fail if there were forbidden characters
        # but this acts as a clear bit of documentation about forbidden characters
        assert c not in "10ilo"
        # the allowed set is the lowercase letters and digits, excluding the forbidden characters
        # so as well as the restriction above, no uppercase, punctuation or extended unicode chars
        assert c in "abcdefghjkmnpqrstuvwxyz23456789"
    return True


def test_generate_id_length():
    id = identifiers.generate_id()
    assert len(id) == 8


def test_generate_id_first_char_is_unambiguous_letter():
    assert_first_character_valid(identifiers.generate_id())


def test_generate_id_allowed_chars():
    assert_all_characters_valid(identifiers.generate_id())


# Testing the generation of multiple ids.  If we choose to generate a large enough number of ids
# we can be fairly confident that generate_id is not creating ids that violate the properties we expect

def test_generate_ids_count():
    count = 10
    ids = list(identifiers.generate_ids(count))
    assert len(ids) == count


def test_generate_ids_unique():
    count = 10000
    ids = list(identifiers.generate_ids(count))
    # Not guaranteed, but should be highly likely
    # With 32^8 possible ids, the probability of a collision when generating 10000 ids is about 0.
    assert len(set(ids)) == len(ids)


def test_generate_ids_first_char_is_letter():
    all((assert_first_character_valid(generated_id) for generated_id in identifiers.generate_ids(500)))


def test_generate_ids_valid_characters():
    all((assert_all_characters_valid(generated_id) for generated_id in identifiers.generate_ids(500)))
