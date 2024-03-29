package weco.catalogue.source_model.sierra

import weco.sierra.models.identifiers.SierraBibNumber

import java.time.Instant

case class SierraBibRecord(
  id: SierraBibNumber,
  data: String,
  modifiedDate: Instant
) extends AbstractSierraRecord[SierraBibNumber]

case object SierraBibRecord {
  def apply(id: String, data: String, modifiedDate: Instant): SierraBibRecord =
    SierraBibRecord(
      id = SierraBibNumber(id),
      data = data,
      modifiedDate = modifiedDate
    )
}
