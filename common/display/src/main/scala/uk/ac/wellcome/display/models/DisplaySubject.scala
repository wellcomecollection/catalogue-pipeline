package uk.ac.wellcome.display.models

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

object DisplaySubject extends GetIdentifiers {
  def apply(subject: Subject[Minted],
            includesIdentifiers: Boolean): DisplaySubject =
    subject match {
      case Subject(id, label, concepts, _) =>
        DisplaySubject(
          id = id.maybeCanonicalId,
          identifiers = getIdentifiers(id, includesIdentifiers),
          label = label,
          concepts =
            concepts.map(DisplayAbstractRootConcept(_, includesIdentifiers))
        )
    }
}
