"""
Geneerates a series of Wellcome Canonical Identifiers.

A canonical identifier is a unique identifier for anything in the Wellcome catalogue.

Canonical IDs are chosen for the following properties:

*   They should be **short**, ideally something that fits on a single post-it note
*   They should be **unambiguous**, so they don't use characters which can look ambiguous (e.g. letter `O` and numeral `0`)
*   They should be **URL safe**

A canonical id is an 8-character string, made up of lowercase letters and digits, excluding `O`, O`, `I`, `L` and `1`(to avoid ambiguity).
8 characters allows for 32^8 = 1 trillion unique identifiers, which should be enough for the foreseeable future.
"""
from collections.abc import Generator
import random
import string

forbidden_letters = {"o", "i", "l"}

number_range = [str(n) for n in range(2, 10)]
letter_range = [c for c in string.ascii_lowercase if c not in forbidden_letters]
allowed_character_set = letter_range + number_range
IDENTIFIER_LENGTH = 8


def generate_id() -> str:
    chars = [
        random.choice(letter_range)
        if i == 0 else random.choice(allowed_character_set)
        for i in range(IDENTIFIER_LENGTH)
    ]
    return ''.join(chars)


def generate_ids(count: int) -> Generator[str]:
    i = 0
    while i < count:
        yield generate_id()
        i += 1
