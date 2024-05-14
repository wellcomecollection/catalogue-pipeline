package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Genre
import weco.pipeline.transformer.marc_common.transformers.MarcGenres
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.pipeline.transformer.transformers.ConceptsTransformer
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

// Populate wwork:genres
//
// Use MARC field "655".
//
// Within a MARC 655 tag, there's:
//
//    - a primary concept (subfield $a); and
//    - subdivisions (subfields $v, $x, $y and $z)
//
// The primary concept can be identified, and the subdivisions serve
// to add extra context.
//
// We construct the Genre as follows:
//
//    - label is the concatenation of $a, $v, $x, $y and $z in order,
//      separated by a hyphen ' - '.
//    - concepts is a List[Concept] populated in order of the subfields:
//
//        * $a => Concept
//          Optionally with an identifier.  We look in subfield $0 for the
//          identifier value, then second indicator for the authority.
//
//        * $v => Concept
//        * $x => Concept
//        * $y => Period
//        * $z => Place
//
//      Note that only concepts from subfield $a are identified; everything
//      else is unidentified.
//
object SierraGenres
    extends SierraDataTransformer
    with SierraQueryOps
    with SierraConcepts
    with ConceptsTransformer
    with SierraMarcDataConversions {

  type Output = List[Genre[IdState.Unminted]]

  def apply(bibData: SierraBibData): List[Genre[IdState.Unminted]] =
    MarcGenres(bibData).toList
}
