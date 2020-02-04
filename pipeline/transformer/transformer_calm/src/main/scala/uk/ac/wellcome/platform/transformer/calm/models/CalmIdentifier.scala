package uk.ac.wellcome.platform.transformer.calm.models

import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}

object CalmIdentifier {
  def recordId(value: String): SourceIdentifier =
    SourceIdentifier(
      value = value,
      ontologyType = "IdentifierType",
      identifierType = IdentifierType("calm-record-id"))

  def refNo(value: String): SourceIdentifier =
    SourceIdentifier(
      value = value,
      ontologyType = "IdentifierType",
      identifierType = IdentifierType("calm-ref-no"))

  def altRefNo(value: String): SourceIdentifier =
    SourceIdentifier(
      value = value,
      ontologyType = "IdentifierType",
      identifierType = IdentifierType("calm-altref-no"))
}
