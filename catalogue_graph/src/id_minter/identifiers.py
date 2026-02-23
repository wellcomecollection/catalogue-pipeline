"""
Generates a series of Wellcome Canonical Identifiers.

A canonical identifier is a unique identifier for anything in the Wellcome catalogue.

Canonical IDs are chosen for the following properties:

*   They should be **short**, ideally something that fits on a single post-it note
*   They should be **unambiguous**, so they don't use characters which can look ambiguous (e.g. letter `O` and numeral `0`)
*   They should be **URL safe**

A canonical id is an 8-character string, made up of lowercase letters and digits, excluding `O`, `0`, `I`, `L` and `1` (to avoid ambiguity).
The first character is always a letter (not a digit), so that identifiers are safe to use as XML identifiers.
This gives 23 * 31^7 ≈ 0.6 trillion unique identifiers, which should be enough for the foreseeable future.
"""

import random
import string
from collections.abc import Generator

FORBIDDEN_LETTERS = {"o", "i", "l"}

NUMBER_RANGE = [str(n) for n in range(2, 10)]
LETTER_RANGE = [c for c in string.ascii_lowercase if c not in FORBIDDEN_LETTERS]
ALLOWED_CHARACTER_SET = LETTER_RANGE + NUMBER_RANGE
IDENTIFIER_LENGTH = 8


def generate_id() -> str:
    chars = [
        random.choice(LETTER_RANGE) if i == 0 else random.choice(ALLOWED_CHARACTER_SET)
        for i in range(IDENTIFIER_LENGTH)
    ]
    return "".join(chars)


def generate_ids(count: int) -> Generator[str]:
    i = 0
    while i < count:
        yield generate_id()
        i += 1
