import pytest

from adapters.transformers.marc.parsers.roman import roman_numeral


@pytest.mark.parametrize(
    "text, value",
    [
        ("M.DCC.XLV.", 1745),
        ("M,DCC,LXXV", 1775),
        ("MDCCLXXXVIII", 1788),
        ("mdlxii", 1562),
        ("M. D. XXVIII", 1528),
        ("MCMXCIX", 1999),
        ("iv", 4),
        ("MCCCCCII", 1502),
        ("M. cccc.Lxxxxi", 1491),
        ("M.D.xxviij", 1528),
        ("M.D.XLiiij", 1544),
    ],
)
def test_well_formed_numerals(text: str, value: int) -> None:
    assert roman_numeral(text) == value


@pytest.mark.parametrize(
    "text", ["mill", "civil", "mid", "mdcic", "m.dcc.lxl", "", "..."]
)
def test_anything_else_is_none(text: str) -> None:
    assert roman_numeral(text) is None
