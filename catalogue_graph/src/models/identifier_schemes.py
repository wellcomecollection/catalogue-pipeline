"""
Registry of identifier schemes, holding the scheme id and display label
together for every displayable scheme, including those only the legacy Scala
transformers emit (the ingestor labels works from all sources).

Schemes carried by works must also exist in the Scala internal model
(IdentifierType.scala), or the merger fails to decode them; a test enforces
this. Concept-source schemes minted in the graph pipeline never pass through
the Scala stages, so they set in_scala_model=False.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class IdentifierScheme:
    id: str
    label: str
    in_scala_model: bool = True


_registry: dict[str, IdentifierScheme] = {}


def _scheme(
    scheme_id: str, label: str, in_scala_model: bool = True
) -> IdentifierScheme:
    scheme = IdentifierScheme(scheme_id, label, in_scala_model)
    if scheme_id in _registry:
        raise ValueError(f"duplicate scheme id: {scheme_id}")
    _registry[scheme_id] = scheme
    return scheme


TEI_MANUSCRIPT_ID = _scheme("tei-manuscript-id", "Tei manuscript id")
MIRO_IMAGE_NUMBER = _scheme("miro-image-number", "Miro image number")
MIRO_LIBRARY_REFERENCE = _scheme("miro-library-reference", "Miro library reference")
SIERRA_SYSTEM_NUMBER = _scheme("sierra-system-number", "Sierra system number")
SIERRA_IDENTIFIER = _scheme("sierra-identifier", "Sierra identifier")
EBSCO_ALT_LOOKUP = _scheme("ebsco-alt-lookup", "EBSCO lookup identifier")
FOLIO_INSTANCE = _scheme("folio-instance", "Folio instance")
FOLIO_ITEM = _scheme("folio-item", "Folio item")
FOLIO_INSTANCE_HRID = _scheme("folio-instance-hrid", "Folio instance HRID")
LC_GMGPC = _scheme("lc-gmgpc", "Library of Congress Thesaurus for Graphic Materials")
LC_SUBJECTS = _scheme("lc-subjects", "Library of Congress Subject Headings (LCSH)")
LC_NAMES = _scheme("lc-names", "Library of Congress Name authority records")
NLM_MESH = _scheme("nlm-mesh", "Medical Subject Headings (MeSH) identifier")
CALM_REF_NO = _scheme("calm-ref-no", "Calm RefNo")
CALM_ALTREF_NO = _scheme("calm-altref-no", "Calm AltRefNo")
CALM_RECORD_ID = _scheme("calm-record-id", "Calm RecordIdentifier")
MIMSY_REFERENCE = _scheme("mimsy-reference", "Mimsy reference")
ISBN = _scheme("isbn", "International Standard Book Number")
ISSN = _scheme("issn", "ISSN")
METS = _scheme("mets", "METS")
METS_IMAGE = _scheme("mets-image", "METS image")
WELLCOME_DIGCODE = _scheme("wellcome-digcode", "Wellcome digcode")
ICONOGRAPHIC_NUMBER = _scheme("iconographic-number", "Iconographic number")
VIAF = _scheme("viaf", "VIAF: The Virtual International Authority File")
FIHRIST = _scheme("fihrist", "Fihrist Authority")
BL_ESTC_CITATION_NO = _scheme(
    "bl-estc-citation-no", "British Library English Short Title Catalogue"
)
LABEL_DERIVED = _scheme(
    "label-derived", "Identifier derived from the label of the referent"
)
WELLCOME_ACCESSION_NUMBER = _scheme("wellcome-accession-number", "Accession number")
WIKIDATA = _scheme("wikidata", "Wikidata", in_scala_model=False)
WECO_AUTHORITY = _scheme("weco-authority", "Wellcome Concepts", in_scala_model=False)
AXIELL_GUID = _scheme("axiell-guid", "Axiell GUID")


def all_schemes() -> list[IdentifierScheme]:
    return list(_registry.values())


IDENTIFIER_LABEL_MAPPING: dict[str, str] = {s.id: s.label for s in _registry.values()}
