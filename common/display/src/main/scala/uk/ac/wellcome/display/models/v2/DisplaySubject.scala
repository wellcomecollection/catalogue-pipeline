package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Subject",
  description = "A subject"
)
case class DisplaySubject(
  id: Option[String] = None,
  identifiers: Option[List[DisplayIdentifierV2]] = None,
  @Schema(description = "A label given to a thing.") label: String,
  @Schema(description = "Relates a subject to a list of concepts.") concepts: List[
    DisplayAbstractRootConcept],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Subject"
)

object DisplaySubject {
  def apply(
    displayableSubject: Displayable[Subject[Displayable[AbstractRootConcept]]],
    includesIdentifiers: Boolean): DisplaySubject = {
    displayableSubject match {
      case Unidentifiable(subject: Subject[Displayable[AbstractRootConcept]]) =>
        DisplaySubject(
          id = None,
          identifiers = None,
          label = subject.label,
          concepts = subject.concepts.map {
            DisplayAbstractRootConcept(
              _,
              includesIdentifiers = includesIdentifiers)
          },
          ontologyType = subject.ontologyType
        )
      case Identified(
          subject: Subject[Displayable[AbstractRootConcept]],
          canonicalId,
          sourceIdentifier,
          otherIdentifiers) =>
        DisplaySubject(
          id = Some(canonicalId),
          identifiers =
            if (includesIdentifiers)
              Some(
                (sourceIdentifier +: otherIdentifiers).map(
                  DisplayIdentifierV2(_)))
            else None,
          label = subject.label,
          concepts = subject.concepts.map {
            DisplayAbstractRootConcept(
              _,
              includesIdentifiers = includesIdentifiers)
          },
          ontologyType = subject.ontologyType
        )
    }
  }
}
