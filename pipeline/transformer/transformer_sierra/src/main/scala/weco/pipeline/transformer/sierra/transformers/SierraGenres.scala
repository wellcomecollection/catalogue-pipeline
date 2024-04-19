package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.catalogue.internal_model.work.{AbstractConcept, Genre, GenreConcept}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.pipeline.transformer.transformers.ConceptsTransformer
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
    with SierraConcepts
    with ConceptsTransformer
    with SierraMarcDataConversions {

  type Output = List[Genre[IdState.Unminted]]
  override protected def getLabel(field: MarcField): Option[String] =
    None // TODO
  def apply(bibData: SierraBibData): List[Genre[IdState.Unminted]] =
    bibData
      .varfieldsWithTag("655")
      .flatMap {
        varField =>
          val (primarySubfields, subdivisionSubfields) =
            getLabelSubfields(varField)

          val label = getLabel(primarySubfields, subdivisionSubfields)
          val concepts = getPrimaryConcept(
            primarySubfields,
            varField = varField
          ) ++ getSubdivisions(subdivisionSubfields)
          label match {
            case "" => None
            case nonEmptyLabel =>
              Some(Genre(label = nonEmptyLabel, concepts = concepts).normalised)
          }
      }
      .distinct

  private def identifyPrimaryConcept(varField: VarField): IdState.Unminted = {
    // For Genres, the identifier found in $0 is assigned to the primary concept.
    // This is in contrast to Subjects, where it is assigned to the Subject itself.
    // However, for a subject, if no identifier is found, one is created that represents
    // the whole field.  In Genres, this should not be the case, because the primary concept
    // will get an identifier made for the concept itself on its own.
    // This method fixes that inconsistency by discarding the LabelDerived identifier
    // It's a bit hacky, but I hope it will go away at some point.
    val wholeFieldConceptId = getIdState(
      ontologyType = "Genre",
      field = varField
    )
    wholeFieldConceptId match {
      case identifiable: IdState.Identifiable
          if identifiable.sourceIdentifier.identifierType == IdentifierType.LabelDerived =>
        IdState.Unidentifiable

      case other => other
    }

  }
  // Extract the primary concept, which comes from subfield $a.  This is the
  // only concept which might be identified.
  private def getPrimaryConcept(
    primarySubfields: List[Subfield],
    varField: VarField
  ): List[AbstractConcept[IdState.Unminted]] =
    primarySubfields.map {
      subfield =>
        GenreConcept(
          id = identifyPrimaryConcept(varField),
          label = subfield.content
        ).normalised.identifiable()
    }
}
