package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.models.work.internal.IdentifierType

case object CalmIdentifierTypes {
  def recordId = IdentifierType("calm-record-id")
  def refNo = IdentifierType("calm-ref-no")
  def altRefNo = IdentifierType("calm-altref-no")
}
