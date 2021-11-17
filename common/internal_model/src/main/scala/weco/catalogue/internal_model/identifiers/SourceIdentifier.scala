package weco.catalogue.internal_model.identifiers

/** An identifier received from one of the original sources */
case class SourceIdentifier(
  identifierType: IdentifierType,
  ontologyType: String,
  value: String
) {
  require(
    value == value.trim,
    s"SourceIdentifier value contains whitespaces: <$value>")

  override def toString = s"<$ontologyType>${identifierType.id}/$value"
}
