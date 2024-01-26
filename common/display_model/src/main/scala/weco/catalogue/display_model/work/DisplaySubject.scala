package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.identifiers.{DisplayIdentifier, GetIdentifiers}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject

case class DisplaySubject(
  id: Option[String] = None,
  identifiers: Option[List[DisplayIdentifier]] = None,
  label: String,
  concepts: List[
    DisplayAbstractRootConcept
  ],
  @JsonKey("type") ontologyType: String = "Subject"
)

object DisplaySubject extends GetIdentifiers {
  def apply(
    subject: Subject[IdState.Minted],
    includesIdentifiers: Boolean
  ): DisplaySubject =
    subject match {
      case Subject(id, label, concepts) =>
        DisplaySubject(
          id = id.maybeCanonicalId.map { _.underlying },
          identifiers = getIdentifiers(id, includesIdentifiers),
          label = label,
          concepts = concepts.map(DisplayAbstractRootConcept(_, includesIdentifiers))
        )
    }
}
