"""Roman numerals as printed in early book dates, "M.DCC.XLV." or "M,DCC,LXXV"."""

import re

VALUES = {"m": 1000, "d": 500, "c": 100, "l": 50, "x": 10, "v": 5, "i": 1}
# Thousands, then hundreds, tens and units, each written either as a run of the same letter or as a
# subtractive pair such as "cm" or "iv". Runs may be longer than the modern three, because early
# printers wrote additive forms such as "MCCCCC" for 1500 and "LXXXX" for 90.
NUMERAL = re.compile(r"m{0,4}(cm|cd|d?c{0,5})(xc|xl|l?x{0,4})(ix|iv|v?i{0,4})")


def roman_numeral(text: str) -> int | None:
    """The value of a roman numeral, ignoring dots, commas and spaces between its groups.

    Returns None for anything that is not a well-formed numeral, including words that happen to
    use only numeral letters, such as "mill" or "civil". A "j" counts as an "i".
    """
    # early printers wrote a final "i" as "j": "xxviij" is 28
    letters = re.sub(r"[.,\s]", "", text.lower()).replace("j", "i")
    if not letters or not NUMERAL.fullmatch(letters):
        return None
    values = [VALUES[ch] for ch in letters]
    # a letter before a larger one is subtracted, as in "iv" or "xc"
    return sum(
        -v if i + 1 < len(values) and v < values[i + 1] else v
        for i, v in enumerate(values)
    )
