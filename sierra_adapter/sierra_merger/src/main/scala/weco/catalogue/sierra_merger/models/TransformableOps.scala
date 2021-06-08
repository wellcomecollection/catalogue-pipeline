package weco.catalogue.sierra_merger.models

import weco.catalogue.source_model.sierra.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber,
  TypedSierraRecordNumber
}
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraBibRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord,
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
    override def create(id: SierraBibNumber,
                        bibRecord: SierraBibRecord): SierraTransformable = {
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

    override def remove(
      transformable: SierraTransformable,
      bibRecord: SierraBibRecord): Option[SierraTransformable] =
      throw new RuntimeException(
        s"We should never be removing a bib record from a SierraTransformable (${transformable.sierraId})")
  }

  trait SubrecordTransformableOps[
    Id <: TypedSierraRecordNumber, Record <: AbstractSierraRecord[Id]]
      extends TransformableOps[Record] {
    implicit val recordOps: RecordOps[Record]

    def getRecords(t: SierraTransformable): Map[Id, Record]
    def setRecords(t: SierraTransformable,
                   records: Map[Id, Record]): SierraTransformable

    override def create(sierraId: SierraBibNumber,
                        record: Record): SierraTransformable = {
      val t = SierraTransformable(sierraId = sierraId)
      val newRecords = Map(record.id -> record)

      setRecords(t, newRecords)
    }

    override def add(t: SierraTransformable,
                     record: Record): Option[SierraTransformable] = {
      if (!recordOps.getBibIds(record).contains(t.sierraId)) {
        throw new RuntimeException(
          s"Non-matching bib id ${t.sierraId} in ${recordOps.getBibIds(record)}")
      }

      // We can decide whether to insert the new data in two steps:
      //
      //  - Do we already have any data for this item?  If not, we definitely
      //    need to merge this record.
      //  - If we have existing data, is it newer or older than the update we've
      //    just received?  If the existing data is older, we need to merge the
      //    new record.
      //
      val isNewerData = {
        getRecords(t).get(record.id) match {
          case Some(existing) =>
            record.modifiedDate.isAfter(existing.modifiedDate) ||
              record.modifiedDate == existing.modifiedDate
          case None => true
        }
      }

      if (isNewerData) {
        val newRecords = getRecords(t) ++ Map(record.id -> record)
        Some(setRecords(t, newRecords))
      } else {
        None
      }
    }

    override def remove(t: SierraTransformable,
                        record: Record): Option[SierraTransformable] = {
      if (!recordOps.getUnlinkedBibIds(record).contains(t.sierraId)) {
        throw new RuntimeException(
          s"Non-matching bib id ${t.sierraId} in ${recordOps.getUnlinkedBibIds(record)}")
      }

      val newRecords =
        getRecords(t)
          .filterNot {
            case (id, currentRecord) =>
              val matchesCurrentRecord = id == record.id

              val modifiedAfter = record.modifiedDate.isAfter(
                currentRecord.modifiedDate
              )

              matchesCurrentRecord && modifiedAfter
          }

      if (getRecords(t) != newRecords) {
        Some(setRecords(t, newRecords))
      } else {
        None
      }
    }
  }

  implicit val itemTransformableOps =
    new SubrecordTransformableOps[SierraItemNumber, SierraItemRecord] {
      override implicit val recordOps: RecordOps[SierraItemRecord] =
        RecordOps.itemRecordOps

      override def getRecords(
        t: SierraTransformable): Map[SierraItemNumber, SierraItemRecord] =
        t.itemRecords

      override def setRecords(
        t: SierraTransformable,
        itemRecords: Map[SierraItemNumber, SierraItemRecord])
        : SierraTransformable =
        t.copy(itemRecords = itemRecords)
    }

  implicit val holdingsTransformableOps =
    new SubrecordTransformableOps[SierraHoldingsNumber, SierraHoldingsRecord] {
      override implicit val recordOps: RecordOps[SierraHoldingsRecord] =
        RecordOps.holdingsRecordOps

      override def getRecords(t: SierraTransformable)
        : Map[SierraHoldingsNumber, SierraHoldingsRecord] =
        t.holdingsRecords

      override def setRecords(
        t: SierraTransformable,
        holdingsRecords: Map[SierraHoldingsNumber, SierraHoldingsRecord])
        : SierraTransformable =
        t.copy(holdingsRecords = holdingsRecords)
    }

  implicit val orderTransformableOps =
    new SubrecordTransformableOps[SierraOrderNumber, SierraOrderRecord] {
      override implicit val recordOps: RecordOps[SierraOrderRecord] =
        RecordOps.orderRecordOps

      override def getRecords(
        t: SierraTransformable): Map[SierraOrderNumber, SierraOrderRecord] =
        t.orderRecords

      override def setRecords(
        t: SierraTransformable,
        orderRecords: Map[SierraOrderNumber, SierraOrderRecord])
        : SierraTransformable =
        t.copy(orderRecords = orderRecords)
    }
}
