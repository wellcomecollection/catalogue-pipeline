package uk.ac.wellcome.sierra_adapter.model

case class SierraTransformable(
  sierraId: SierraBibNumber,
  maybeBibRecord: Option[SierraBibRecord] = None,
  itemRecords: Map[SierraItemNumber, SierraItemRecord] = Map()
)

object SierraTransformable {
  def apply(bibRecord: SierraBibRecord): SierraTransformable =
    SierraTransformable(
      sierraId = bibRecord.id,
      maybeBibRecord = Some(bibRecord))

}
