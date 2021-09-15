package weco.catalogue.internal_model.identifiers

import io.circe.{Decoder, Encoder}

/** An identifier received from one of the original sources */
case class SourceIdentifier(
  identifierType: IdentifierType,
  ontologyType: String,
  value: String
) {
  require(
    value == value.trim,
    s"SourceIdentifier value contains whitespaces: <$value>")

  override def toString = s"${identifierType.id}/$value"
}

case object SourceIdentifier {
  implicit val encoder: Encoder[SourceIdentifier] =
    Encoder.forProduct3[SourceIdentifier, IdentifierType, String, String]("identifierType", "ontologyType", "value")(id => (id.identifierType, id.ontologyType, id.value))

  implicit val decoder: Decoder[SourceIdentifier] =
    Decoder.forProduct3[SourceIdentifier, IdentifierType, String, String]("identifierType", "ontologyType", "value")((identifierType, ontologyType, value) => SourceIdentifier(identifierType, ontologyType, value))
}
