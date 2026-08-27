"""Short-description derivation, pinned to real archive collection-root descriptions.

Every input is taken from the 997 collection roots in works-indexed-2026-07-30, trimmed where a
whole description would be unreadable in a test.
"""

import pytest

from ingestor.models.display.short_description import derive_short_description


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        # Nothing to do: the first sentence already describes the collection.
        (
            "Papers of noted Jungian analyst Michael Fordham.",
            "Papers of noted Jungian analyst Michael Fordham.",
        ),
        # A paragraph with no terminal punctuation must not run into the next one.
        (
            "<p>Administrative records relating to the foundation of the Society</p>"
            "<p>Papers of members, formerly held by the Society.</p>",
            "Administrative records relating to the foundation of the Society",
        ),
        # Markup is removed, entities decoded.
        (
            "<p>Cooper, McDougall &amp; Robertson Ltd records.</p>",
            "Cooper, McDougall & Robertson Ltd records.",
        ),
    ],
)
def test_first_sentence_of_a_plain_description(description: str, expected: str) -> None:
    assert derive_short_description(description) == expected


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        # "Vol." and "c." are the abbreviations that actually bite in this corpus.
        (
            "Vol. 2 of the series covers 1900-1910. Vol. 3 covers 1911-1920.",
            "Vol. 2 of the series covers 1900-1910.",
        ),
        (
            "10 recordings relating to Joan Malleson's sex therapy, c. early 1950s "
            "(she died in 1956).",
            "10 recordings relating to Joan Malleson's sex therapy, c. early 1950s "
            "(she died in 1956).",
        ),
        ("UCL Inst. of Urology 17 Sept 2003", "UCL Inst. of Urology 17 Sept 2003"),
        # Bibliographic abbreviations from manuscript-level records.
        (
            "The 38 ll. at the beginning contain medical notes and prescriptions.",
            "The 38 ll. at the beginning contain medical notes and prescriptions.",
        ),
        (
            "Liber amicorum: containing 98 signed entries by various late 18th cent. "
            "celebrities, mainly German.",
            "Liber amicorum: containing 98 signed entries by various late 18th cent. "
            "celebrities, mainly German.",
        ),
    ],
)
def test_abbreviations_do_not_end_the_sentence(description: str, expected: str) -> None:
    assert derive_short_description(description) == expected


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        # A full stop after the notice, so it is already its own sentence.
        (
            "This collection is uncatalogued. Papers and audio material relating to the "
            "Laboratory Technicians Oral History Project.",
            "Papers and audio material relating to the Laboratory Technicians Oral History "
            "Project.",
        ),
        # A colon, which is not a sentence boundary, so the notice has to be split off.
        (
            "The following is an interim description which may change when detailed "
            "cataloguing takes place in future: Archives relating to the activities of the "
            "campaign, 1970s-1990s.",
            "Archives relating to the activities of the campaign, 1970s-1990s.",
        ),
        # A semicolon.
        (
            "The following is an interim description which may change when detailed "
            "cataloguing takes place in future; Papers created and accumulated by the "
            "Steroid Aid Group (SAG), 1979-2014.",
            "Papers created and accumulated by the Steroid Aid Group (SAG), 1979-2014.",
        ),
        # No separator at all, which the source data does contain.
        (
            "The following is an interim description which may change when detailed "
            "cataloguing takes place in future A large collection reflecting Tizard's work "
            "in paediatrics.",
            "A large collection reflecting Tizard's work in paediatrics.",
        ),
        # The bare wording, with no "The following is" lead-in.
        (
            "Interim description pending cataloguing: F A Jenner's writings in various "
            "languages.",
            "F A Jenner's writings in various languages.",
        ),
        # Wrapped in quotation marks, with the colon inside the closing quote.
        (
            '"The following is an interim description which may change when detailed '
            'cataloguing takes place in future:" Diaries relating to his varied career.',
            "Diaries relating to his varied career.",
        ),
        # Two notices in sequence.
        (
            "This collection is uncatalogued. The following is a temporary description which "
            "may change when detailed cataloguing takes place in the future. Papers of the "
            "Oral History Project.",
            "Papers of the Oral History Project.",
        ),
    ],
)
def test_cataloguing_notices_are_dropped(description: str, expected: str) -> None:
    assert derive_short_description(description) == expected


def test_a_description_that_is_only_a_notice_publishes_nothing() -> None:
    """Better an absent field than a card reading "This collection is uncatalogued"."""
    assert (
        derive_short_description(
            "The following is an interim description which may change when detailed "
            "cataloguing takes place in future."
        )
        is None
    )


def test_colon_lead_in_is_joined_to_what_follows() -> None:
    """Known weakness: a lead-in introducing a list still ends on the first item's comma."""
    result = derive_short_description(
        "Papers of Colonel Donovan: Correspondence with Sir Ronald Ross 1903."
    )
    assert (
        result == "Papers of Colonel Donovan: Correspondence with Sir Ronald Ross 1903."
    )


@pytest.mark.parametrize("description", [None, "", "   ", "<p></p>"])
def test_empty_descriptions_publish_nothing(description: str | None) -> None:
    assert derive_short_description(description) is None


def test_the_value_is_always_part_of_the_description() -> None:
    """Nothing is generated: whatever is published was written by a cataloguer."""
    descriptions = [
        "Papers of noted Jungian analyst Michael Fordham.",
        "The following is an interim description which may change when detailed cataloguing "
        "takes place in future: Archives relating to the campaign.",
        "<p>Item no. 5 of the series. Another sentence.</p>",
        "Vol. 2 of the series covers 1900-1910. Vol. 3 covers 1911-1920.",
    ]
    for description in descriptions:
        value = derive_short_description(description)
        if value is not None:
            # Compared on words, since the markup and whitespace are normalised on the way out.
            assert set(value.split()) <= set(
                description.replace("<p>", " ").replace("</p>", " ").split()
            )


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        # Cataloguing status.
        (
            "This archive is not yet catalogued. Papers reflecting his career in psychiatry.",
            "Papers reflecting his career in psychiatry.",
        ),
        # Sensitivity and closure notes, which arrive several in a row.
        (
            "The following is an interim description which may change when detailed "
            "cataloguing takes place in future: Please note that this archive contains "
            "patient data that is highly sensitive in nature. When the archive is "
            "catalogued, the patient data will require closure for the lifetime of the data "
            "subjects. For fuller information on how the library handles sensitive archival "
            "data, see our Access Policy. Case records relating to Bywaters's work.",
            "Case records relating to Bywaters's work.",
        ),
        # Where the description came from, rather than what it describes.
        (
            "Catalogue descriptions adapted from information provided by a friend of the "
            "donor. Papers and articles by Godfrey, including drafts.",
            "Papers and articles by Godfrey, including drafts.",
        ),
        # "Note:" prefix.
        (
            "Note: This archive includes sensitive personal data. Archives of Therese "
            "Woodcock relating to Lowenfeld Mosaics.",
            "Archives of Therese Woodcock relating to Lowenfeld Mosaics.",
        ),
    ],
)
def test_status_and_access_notes_are_dropped(description: str, expected: str) -> None:
    assert derive_short_description(description) == expected


def test_a_record_that_is_only_a_custody_note_publishes_nothing() -> None:
    assert (
        derive_short_description(
            "This collection is no longer held by Wellcome Collection and cannot be "
            "consulted here. It has been returned to LSHTM."
        )
        is None
    )


def test_a_real_description_mentioning_sensitive_material_is_kept() -> None:
    """The note patterns are anchored, so this must not be mistaken for one."""
    description = (
        "This collection contains records of the Consumers' Advisory Group, including "
        "sensitive material relating to clinical trials."
    )
    assert derive_short_description(description) == description


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        # The lead-in ends its own <p>, with the list in a following <ul>.
        (
            "<p>The archive contains material created by Audrey Amiss over the course of "
            "her life, including:</p><ul><li>Sketchbooks</li><li>Artworks</li></ul>",
            "The archive contains material created by Audrey Amiss over the course of her "
            "life.",
        ),
        # No comma before it, and the list is shelfmarks rather than prose.
        (
            "<p>Comprises certificates awarded to Henry Walpole Hooper, including:</p>"
            "<p>MS.7112/1: Certificate of matriculation, University of London.</p>",
            "Comprises certificates awarded to Henry Walpole Hooper.",
        ),
    ],
)
def test_including_lead_in_is_closed_not_joined(
    description: str, expected: str
) -> None:
    """What follows is usually a truncated list, so end the sentence rather than pull the list in."""
    assert derive_short_description(description) == expected
