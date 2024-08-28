package weco.pipeline.ingestor.common.models

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{Location, PhysicalLocation}
import weco.catalogue.internal_model.work.{AbstractConcept, Genre}

object ValueTransforms {
  // Trailing full stops are inconsistently present on concept and subject labels.
  // Because the filters that operate on these fields treat the whole field as a
  // strict matching keyword, this inconsistency means that expected records are not
  // found, and that multiple similar entries appear in the aggregations, differenced
  // only by the existence of a trailing `.`
  // If other systemic differences occur that are not deliberately contrasting, then
  // they can be added here.
  def queryableLabel(label: String): String = label.stripSuffix(".")

  def genreConcepts(
    genres: List[Genre[IdState.Minted]]
  ): List[AbstractConcept[IdState.Minted]] =
    // Only the first concept counts, the others include things like places and periods that help
    // a reader understand more about the genre of a given item, but do not contribute meaningfully
    // to a filter, so are excluded from the query section.
    genres.flatMap(_.concepts.headOption)

  implicit class IdStateOps(ids: Seq[IdState.Minted]) {
    def canonicalIds: List[String] =
      ids.flatMap(_.maybeCanonicalId).map(_.underlying).toList

    def sourceIdentifiers: Seq[String] = ids
      .collect {
        case IdState.Identified(_, sourceIdentifier, otherIdentifiers) =>
          sourceIdentifier +: otherIdentifiers
      }
      .flatten
      .map(_.value)
  }

  // Shelfmarks are only available on physical locations
  def locationShelfmark(location: Location): Option[String] =
    location match {
      case PhysicalLocation(_, _, _, shelfmark, _) => shelfmark
      case _                                       => None
    }
}
