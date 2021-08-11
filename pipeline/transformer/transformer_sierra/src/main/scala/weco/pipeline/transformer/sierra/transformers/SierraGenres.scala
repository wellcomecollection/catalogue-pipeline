package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Concept, Genre}
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.{Subfield, VarField}

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
    with SierraConcepts {

  type Output = List[Genre[IdState.Unminted]]

  def apply(bibData: SierraBibData) =
    bibData
      .varfieldsWithTag("655")
      .map { varField =>
        val (primarySubfields, subdivisionSubfields) =
          varField
            .subfieldsWithTags("a", "v", "x", "y", "z")
            .partition { _.tag == "a" }

        val label = getLabel(primarySubfields, subdivisionSubfields)
        val concepts = getPrimaryConcept(primarySubfields, varField = varField) ++ getSubdivisions(
          subdivisionSubfields)

        Genre.normalised(label = label, concepts = concepts)
      }
      .distinct

  // Extract the primary concept, which comes from subfield $a.  This is the
  // only concept which might be identified.
  private def getPrimaryConcept(
                                 primarySubfields: List[Subfield],
                                 varField: VarField): List[Concept[IdState.Unminted]] =
    primarySubfields.map { subfield =>
      val concept = Concept.normalised(label = subfield.content)
      concept.copy(id = identifyConcept(concept, varField))
    }
}
