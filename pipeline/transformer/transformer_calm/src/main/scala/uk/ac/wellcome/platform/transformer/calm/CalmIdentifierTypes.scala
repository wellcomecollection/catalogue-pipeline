package uk.ac.wellcome.platform.transformer.calm

import weco.catalogue.internal_model.identifiers.IdentifierType

case object CalmIdentifierTypes {
  def recordId = IdentifierType("calm-record-id")
  def refNo = IdentifierType("calm-ref-no")
  def altRefNo = IdentifierType("calm-altref-no")
}
