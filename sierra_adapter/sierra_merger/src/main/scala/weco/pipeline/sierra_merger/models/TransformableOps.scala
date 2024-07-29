package weco.pipeline.sierra_merger.models

import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraBibRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord,
  SierraTransformable
}
import weco.sierra.models.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber,
  TypedSierraRecordNumber
}

trait TransformableOps[SierraRecord <: AbstractSierraRecord[_]] {
  def create(id: SierraBibNumber, r: SierraRecord): SierraTransformable

  def add(t: SierraTransformable, r: SierraRecord): Option[SierraTransformable]

  def remove(
    t: SierraTransformable,
    r: SierraRecord
  ): Option[SierraTransformable]
}

object TransformableOps {
  implicit class SierraTransformableOps(t: SierraTransformable) {
    def add[SierraRecord <: AbstractSierraRecord[_]](
      r: SierraRecord
    )(
      implicit ops: TransformableOps[SierraRecord]
    ): Option[SierraTransformable] =
      ops
        .add(t, r)
        .map {
          transformable =>
            transformable.copy(
              modifiedTime = Seq(transformable.modifiedTime, r.modifiedDate).max
            )
        }

    def remove[SierraRecord <: AbstractSierraRecord[_]](
      r: SierraRecord
    )(
      implicit ops: TransformableOps[SierraRecord]
    ): Option[SierraTransformable] =
      ops
        .remove(t, r)
        .map {
          transformable =>
            transformable.copy(
              modifiedTime = Seq(transformable.modifiedTime, r.modifiedDate).max
            )
        }
  }

  implicit val bibTransformableOps = new TransformableOps[SierraBibRecord] {
    override def create(
      id: SierraBibNumber,
      bibRecord: SierraBibRecord
    ): SierraTransformable = {
      assert(id == bibRecord.id)
      SierraTransformable(bibRecord)
    }

    override def add(
      transformable: SierraTransformable,
      bibRecord: SierraBibRecord
    ): Option[SierraTransformable] = {
      if (bibRecord.id != transformable.sierraId) {
        throw new RuntimeException(
          s"Non-matching bib ids ${bibRecord.id} != ${transformable.sierraId}"
        )
      }

      val isNewerData = transformable.maybeBibRecord match {
        case Some(existingBibRecord) =>
          bibRecord.modifiedDate.isAfter(existingBibRecord.modifiedDate) ||
          bibRecord.modifiedDate == existingBibRecord.modifiedDate
        case None => true
      }

      if (isNewerData) {
        val modifiedTime =
          Seq(bibRecord.modifiedDate, transformable.modifiedTime).max
        Some(
          transformable.copy(
            maybeBibRecord = Some(bibRecord),
            modifiedTime = modifiedTime
          )
        )
      } else {
        None
      }
    }

    override def remove(
      transformable: SierraTransformable,
      bibRecord: SierraBibRecord
    ): Option[SierraTransformable] =
      throw new RuntimeException(
        s"We should never be removing a bib record from a SierraTransformable (${transformable.sierraId})"
      )
  }

  trait SubrecordTransformableOps[
    Id <: TypedSierraRecordNumber,
    SierraRecord <: AbstractSierraRecord[Id]
  ] extends TransformableOps[SierraRecord] {
    implicit val recordOps: RecordOps[SierraRecord]

    def getRecords(t: SierraTransformable): Map[Id, SierraRecord]
    def setRecords(
      t: SierraTransformable,
      records: Map[Id, SierraRecord]
    ): SierraTransformable

    override def create(
      sierraId: SierraBibNumber,
      record: SierraRecord
    ): SierraTransformable = {
      val t = SierraTransformable(
        sierraId = sierraId,
        modifiedTime = record.modifiedDate
      )
      val newRecords = Map(record.id -> record)

      setRecords(t, newRecords)
    }

    // Note: we do care about strict equality here, not just "after".
    //
    // In particular, there have been cases where we saw:
    //
    //    - a record be modified and get an "updatedDate: $t"
    //    - the same record be deleted with "updatedDate: $t"
    //
    // In these cases, we want to process the deletion, so we use
    // "latest to the merger wins".  If this is wrong, somebody can
    // update the record in Sierra again to increment the updatedDate.
    private def shouldReplaceExisting(
      existing: SierraRecord,
      newRecord: SierraRecord
    ): Boolean =
      newRecord.modifiedDate.isAfter(existing.modifiedDate) ||
        newRecord.modifiedDate == existing.modifiedDate

    override def add(
      t: SierraTransformable,
      record: SierraRecord
    ): Option[SierraTransformable] = {
      if (!recordOps.getBibIds(record).contains(t.sierraId)) {
        throw new RuntimeException(
          s"Non-matching bib id ${t.sierraId} in ${recordOps.getBibIds(record)}"
        )
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
          case Some(existing) => shouldReplaceExisting(existing, record)
          case None           => true
        }
      }

      if (isNewerData) {
        val newRecords = getRecords(t) ++ Map(record.id -> record)
        Some(setRecords(t, newRecords))
      } else {
        None
      }
    }

    override def remove(
      t: SierraTransformable,
      record: SierraRecord
    ): Option[SierraTransformable] = {
      if (!recordOps.getUnlinkedBibIds(record).contains(t.sierraId)) {
        throw new RuntimeException(
          s"Non-matching bib id ${t.sierraId} in ${recordOps.getUnlinkedBibIds(record)}"
        )
      }

      val newRecords =
        getRecords(t)
          .filterNot {
            case (id, existing) =>
              val hasMatchingId = id == record.id

              hasMatchingId && shouldReplaceExisting(existing, record)
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
        t: SierraTransformable
      ): Map[SierraItemNumber, SierraItemRecord] =
        t.itemRecords

      override def setRecords(
        t: SierraTransformable,
        itemRecords: Map[SierraItemNumber, SierraItemRecord]
      ): SierraTransformable = {
        val modifiedTime =
          (itemRecords.values.map(_.modifiedDate).toSeq :+ t.modifiedTime).max

        t.copy(
          itemRecords = itemRecords,
          modifiedTime = modifiedTime
        )
      }
    }

  implicit val holdingsTransformableOps =
    new SubrecordTransformableOps[SierraHoldingsNumber, SierraHoldingsRecord] {
      override implicit val recordOps: RecordOps[SierraHoldingsRecord] =
        RecordOps.holdingsRecordOps

      override def getRecords(
        t: SierraTransformable
      ): Map[SierraHoldingsNumber, SierraHoldingsRecord] =
        t.holdingsRecords

      override def setRecords(
        t: SierraTransformable,
        holdingsRecords: Map[SierraHoldingsNumber, SierraHoldingsRecord]
      ): SierraTransformable = {
        val modifiedTime = (holdingsRecords.values
          .map(_.modifiedDate)
          .toSeq :+ t.modifiedTime).max

        t.copy(
          holdingsRecords = holdingsRecords,
          modifiedTime = modifiedTime
        )
      }
    }

  implicit val orderTransformableOps =
    new SubrecordTransformableOps[SierraOrderNumber, SierraOrderRecord] {
      override implicit val recordOps: RecordOps[SierraOrderRecord] =
        RecordOps.orderRecordOps

      override def getRecords(
        t: SierraTransformable
      ): Map[SierraOrderNumber, SierraOrderRecord] =
        t.orderRecords

      override def setRecords(
        t: SierraTransformable,
        orderRecords: Map[SierraOrderNumber, SierraOrderRecord]
      ): SierraTransformable = {
        val modifiedTime =
          (orderRecords.values.map(_.modifiedDate).toSeq :+ t.modifiedTime).max

        t.copy(
          orderRecords = orderRecords,
          modifiedTime = modifiedTime
        )
      }
    }
}
