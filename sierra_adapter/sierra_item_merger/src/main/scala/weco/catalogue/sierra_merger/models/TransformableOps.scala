package weco.catalogue.sierra_merger.models

import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraBibNumber,
  SierraBibRecord,
  SierraItemRecord,
  SierraTransformable
}

trait TransformableOps[Record <: AbstractSierraRecord[_]] {
  def create(id: SierraBibNumber, r: Record): SierraTransformable

  def add(t: SierraTransformable, r: Record): Option[SierraTransformable]

  def remove(t: SierraTransformable, r: Record): Option[SierraTransformable]
}

object TransformableOps {
  implicit class SierraTransformableOps(t: SierraTransformable) {
    def add[Record <: AbstractSierraRecord[_]](r: Record)(
      implicit
      ops: TransformableOps[Record]
    ): Option[SierraTransformable] =
      ops.add(t, r)

    def remove[Record <: AbstractSierraRecord[_]](r: Record)(
      implicit
      ops: TransformableOps[Record]
    ): Option[SierraTransformable] =
      ops.remove(t, r)
  }

  implicit val bibTransformableOps = new TransformableOps[SierraBibRecord] {
    override def create(id: SierraBibNumber, bibRecord: SierraBibRecord): SierraTransformable = {
      assert(id == bibRecord.id)
      SierraTransformable(bibRecord)
    }

    override def add(
      transformable: SierraTransformable,
      bibRecord: SierraBibRecord): Option[SierraTransformable] = {
      if (bibRecord.id != transformable.sierraId) {
        throw new RuntimeException(
          s"Non-matching bib ids ${bibRecord.id} != ${transformable.sierraId}")
      }

      val isNewerData = transformable.maybeBibRecord match {
        case Some(bibData) =>
          bibRecord.modifiedDate.isAfter(bibData.modifiedDate) ||
            bibRecord.modifiedDate == bibData.modifiedDate
        case None => true
      }

      if (isNewerData) {
        Some(transformable.copy(maybeBibRecord = Some(bibRecord)))
      } else {
        None
      }
    }

    override def remove(transformable: SierraTransformable, bibRecord: SierraBibRecord): Option[SierraTransformable] =
      throw new RuntimeException(s"We should never be removing a bib record from a SierraTransformable (${transformable.sierraId})")
  }

  implicit val itemTransformableOps = new TransformableOps[SierraItemRecord] {
    override def create(sierraId: SierraBibNumber,
                        itemRecord: SierraItemRecord): SierraTransformable =
      SierraTransformable(
        sierraId = sierraId,
        itemRecords = Map(itemRecord.id -> itemRecord)
      )

    override def add(
      sierraTransformable: SierraTransformable,
      itemRecord: SierraItemRecord): Option[SierraTransformable] = {
      if (!itemRecord.bibIds.contains(sierraTransformable.sierraId)) {
        throw new RuntimeException(
          s"Non-matching bib id ${sierraTransformable.sierraId} in item bib ${itemRecord.bibIds}")
      }

      // We can decide whether to insert the new data in two steps:
      //
      //  - Do we already have any data for this item?  If not, we definitely
      //    need to merge this record.
      //  - If we have existing data, is it newer or older than the update we've
      //    just received?  If the existing data is older, we need to merge the
      //    new record.
      //
      val isNewerData =
        sierraTransformable.itemRecords.get(itemRecord.id) match {
          case Some(existing) =>
            itemRecord.modifiedDate.isAfter(existing.modifiedDate) ||
              itemRecord.modifiedDate == existing.modifiedDate
          case None => true
        }

      if (isNewerData) {
        val itemData = sierraTransformable.itemRecords + (itemRecord.id -> itemRecord)
        Some(sierraTransformable.copy(itemRecords = itemData))
      } else {
        None
      }
    }

    override def remove(
      sierraTransformable: SierraTransformable,
      itemRecord: SierraItemRecord): Option[SierraTransformable] = {
      if (!itemRecord.unlinkedBibIds.contains(sierraTransformable.sierraId)) {
        throw new RuntimeException(
          s"Non-matching bib id ${sierraTransformable.sierraId} in item unlink bibs ${itemRecord.unlinkedBibIds}")
      }

      val itemRecords =
        sierraTransformable.itemRecords
          .filterNot {
            case (id, currentItemRecord) =>
              val matchesCurrentItemRecord = id == itemRecord.id

              val modifiedAfter = itemRecord.modifiedDate.isAfter(
                currentItemRecord.modifiedDate
              )

              matchesCurrentItemRecord && modifiedAfter
          }

      if (sierraTransformable.itemRecords != itemRecords) {
        Some(sierraTransformable.copy(itemRecords = itemRecords))
      } else {
        None
      }
    }
  }
}
