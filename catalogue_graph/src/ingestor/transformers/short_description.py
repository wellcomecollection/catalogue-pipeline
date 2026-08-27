"""Derive a short description from the description a cataloguer already wrote.

The value is always a substring of the existing description, never generated text. Scoped to
archive collection roots, which is the population the archive browse cards need.

The difficulty is not finding the sentence boundary. Two independent splitters agree on 97% of
collection roots. It is that a correctly extracted first sentence is often a note about the state
of the cataloguing rather than a description of the collection, which is what the boilerplate
filter below is for. See catalogue_graph/notebooks/short_description_extraction.ipynb for the
measurements behind each step.
"""

import functools
import re
from typing import Any

from lxml import html as lxml_html
from lxml.html import HtmlElement

# Block-level tags from the sanitiser's allow-list (BASIC_TAGS in adapters/transformers/utils/html),
# plus table and heading tags in case anything upstream lets them through.
BLOCK_TAGS = {
    "p", "div", "br", "li", "ul", "ol", "dl", "dd", "dt",
    "blockquote", "pre", "table", "tr", "td", "th",
    "h1", "h2", "h3", "h4", "h5", "h6",
}  # fmt: skip

# Abbreviations that end in a full stop without ending a sentence. Every entry was observed
# splitting a real record. The list cannot be completed: each one is only found by a record it
# has already broken, and a longer list starts breaking sentences that genuinely end on "MS."
ABBREVIATIONS = {
    "c", "ca", "circa", "vol", "vols", "no", "nos", "p", "pp", "ff",
    "fig", "figs", "ed", "eds", "edn", "edit", "cf", "approx", "dept", "est",
    "decd", "exors", "mons", "rt", "ult", "inst", "fl", "ser", "cat", "bros",
    "sr", "jr", "gen", "maj", "messrs", "mme", "mlle", "capt", "col", "lt", "sgt", "hon",
    "ibid", "op", "viz",
    # Bibliographic description of manuscripts: leaves, manuscripts, century, regarding.
    "l", "ll", "ms", "mss", "cent", "re", "esqre",
}  # fmt: skip

# Preceded by start-of-string or a non-word, non-dot character, so brackets and quotes count as
# a boundary without needing them in the pattern.
_TRAILING_ABBREVIATION = re.compile(r"(?:^|[^\w.])([A-Za-z]{1,6})\.$")

# Cataloguing notes: true, but about the record's handling rather than the collection.
_BOILERPLATE = re.compile(
    r"uncatalogued|interim description|temporary description|summary description"
    r"|not been (fully )?catalogued",
    re.I,
)

# Status, custody, access and sensitivity notes, which arrive as complete sentences and often
# several in a row before the description proper starts. Anchored at the start of the sentence:
# a real description can mention sensitive material without being a note about it, and
# "This collection contains ..." is a common way to open a genuine description.
_NOT_ABOUT_THE_COLLECTION = re.compile(
    r"^(?:"
    r"please note\b"
    r"|note:\s"
    r"|this (?:archive|collection) is not yet catalogued"
    r"|this (?:archive|collection) is no longer held"
    r"|it (?:has been|was) (?:returned|transferred|moved) to\b"
    r"|this (?:archive|collection) contains sensitive material"
    r"|when the (?:archive|collection) is catalogued\b"
    r"|for (?:fuller|further) information on how the library"
    r"|we anticipate that it will not be possible"
    r"|other parts of the (?:archive|collection) will be assessed"
    # Where the description itself came from, rather than what it describes.
    r"|catalogue descriptions? (?:adapted|derived|based)"
    r")",
    re.I,
)

# The provisional-description notice, matched by wording because the separator varies: a full
# stop in some records, a colon or semicolon in others, and in a few nothing at all. Only the
# full stop makes it a sentence of its own, so the rest have to be split off explicitly.
_NOTICE = re.compile(
    r"^\W{0,3}(?:(?:the following (?:is|contains)|this is)\s+an?\s+)?"
    r"(?:interim|temporary|summary|provisional)\s+description"
    r"(?:"
    # The clause ends on a recognised marker, so a separator after it is optional: some records
    # run the notice straight into the description with only a space. Greedy, so the clause ends
    # at the last marker rather than the first: "cataloguing takes place in future" has to
    # consume the "in future" too.
    r"[^.;:]{0,80}(?:in (?:the )?future|cataloguing takes place|is catalogued"
    r"|pending cataloguing)\s*[.;:]?"
    r"|"
    # No marker, so a separator is required. Without this a real description opening "Summary
    # description of the papers of ..." would be cut after the second word.
    r"\s*[.;:]"
    r")"
    # Absorb a closing quote, or a notice written as "... in future:" leaves the stray quote
    # behind as its own sentence.
    r"[\"'’”)\]]*",
    re.I,
)


@functools.cache
def _segmenter() -> Any:
    """Built once: constructing a Segmenter compiles its rule set."""
    import pysbd

    return pysbd.Segmenter(language="en", clean=False)


def _split_sentences(text: str) -> list[str]:
    segments: list[str] = _segmenter().segment(text)
    return [segment.strip() for segment in segments if segment.strip()]


def _html_to_blocks(raw: str) -> list[str]:
    """Plain-text blocks, one per block-level element.

    Blocks rather than one flat string, because a first paragraph with no terminal punctuation
    should end at the paragraph rather than run into the next one.
    """
    # create_parent handles bare text and multiple top-level elements alike, so there is one
    # code path whether or not the description contains markup.
    root = lxml_html.fragment_fromstring(raw, create_parent="div")

    pieces: list[str] = []

    def walk(element: HtmlElement) -> None:
        is_block = element.tag in BLOCK_TAGS
        if is_block:
            pieces.append("\n")
        if element.text:
            pieces.append(element.text)
        for child in element:
            walk(child)
            if child.tail:
                # A tail belongs to the parent's context, not the child's.
                pieces.append(child.tail)
        if is_block:
            pieces.append("\n")

    walk(root)

    blocks = []
    for segment in "".join(pieces).split("\n"):
        collapsed = re.sub(r"\s+", " ", segment).strip()
        if collapsed:
            blocks.append(collapsed)
    return blocks


def _ends_with_abbreviation(sentence: str) -> bool:
    match = _TRAILING_ABBREVIATION.search(sentence.rstrip())
    return bool(match and match.group(1).lower() in ABBREVIATIONS)


def _merge_abbreviations(sentences: list[str]) -> list[str]:
    """Rejoin sentences the splitter broke after an abbreviation such as "c." or "Vol."."""
    merged: list[str] = []
    for sentence in sentences:
        if merged and _ends_with_abbreviation(merged[-1]):
            merged[-1] = f"{merged[-1].rstrip()} {sentence.lstrip()}"
        else:
            merged.append(sentence)
    return merged


def _split_notices(sentences: list[str]) -> list[str]:
    """Make a notice glued to the description its own sentence, so the filter can drop it.

    Applied to every sentence, not just the first: a record can open with one note and carry
    the colon-separated notice in the sentence after it, and filtering that sentence whole
    would take the description with it.
    """
    split: list[str] = []
    for sentence in sentences:
        match = _NOTICE.match(sentence)
        if match is None:
            split.append(sentence)
            continue
        split.append(sentence[: match.end()].strip())
        rest = sentence[match.end() :].strip()
        if rest:
            split.append(rest)
    return split


# "..., including:" introduces a list, and what follows is usually shelfmarks rather than prose.
# The lead-in reads as a complete description once the colon becomes a full stop, so close it
# here rather than letting _join_stubs pull the first list item in.
_TRAILING_INCLUDING = re.compile(r",?\s*including\s*:\s*$", re.I)


def _close_including(sentence: str) -> str:
    return _TRAILING_INCLUDING.sub(".", sentence)


def _join_stubs(sentences: list[str]) -> list[str]:
    """Attach a colon-terminated lead-in to whatever follows it.

    Leaves a trailing comma where the lead-in introduces a list. Handling those properly needs
    a decision about display length that has not been made yet.
    """
    joined: list[str] = []
    for sentence in sentences:
        if joined and joined[-1].rstrip().endswith(":"):
            joined[-1] = f"{joined[-1].rstrip()} {sentence.lstrip()}"
        else:
            joined.append(sentence)
    return joined


# Manuscript-level records often have a bare title as their whole description ("Contents",
# "Arzneibuch.", "Correspondence."). One word is a title or a fragment, not something
# that works as a browse card, so publish nothing rather than a stub.
MINIMUM_WORDS = 2


def derive_short_description(description: str | None) -> str | None:
    """First sentence of the description that describes the collection rather than its cataloguing.

    Returns None when the description is empty, is entirely cataloguing notes, or yields fewer
    than MINIMUM_WORDS. Deliberately does not truncate: the value stays exactly as catalogued
    and the display decides how much of it to show.
    """
    if not description or not description.strip():
        return None

    blocks = _html_to_blocks(description)
    if not blocks:
        return None

    sentences = _split_notices(
        [
            sentence
            for block in blocks
            for sentence in _merge_abbreviations(_split_sentences(block))
        ]
    )

    # Before joining, so a lead-in ending "including:" is closed off rather than pulling in
    # the list that follows it.
    sentences = [_close_including(sentence) for sentence in sentences]

    candidates = _join_stubs(
        [
            sentence
            for sentence in sentences
            if not _BOILERPLATE.search(sentence)
            and not _NOT_ABOUT_THE_COLLECTION.match(sentence)
            # Stray punctuation left behind by the splitter is not a description.
            and re.search(r"[A-Za-z]", sentence)
        ]
    )
    if not candidates:
        # Everything was a note. Publishing one would be worse than publishing nothing.
        return None

    value = candidates[0]
    if len(value.split()) < MINIMUM_WORDS:
        return None
    return value
