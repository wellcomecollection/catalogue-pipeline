package weco.catalogue.source_model.generators

import weco.json.JsonUtil.toJson
import weco.catalogue.source_model.sierra._
import weco.sierra.generators.SierraIdentifierGenerators
import weco.sierra.models.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber
}

import java.time.Instant

trait SierraRecordGenerators extends SierraIdentifierGenerators {
  // A lot of Sierra tests (e.g. mergers) check the behaviour when merging
  // a record with a newer version, or vice versa.  Provide two dates here
  // for convenience.
  val olderDate: Instant = Instant.parse("1999-09-09T09:09:09Z")
  val newerDate: Instant = Instant.parse("2001-01-01T01:01:01Z")

  protected def createTitleVarfield(
    title: String = s"title-${randomAlphanumeric()}"
  ): String =
    s"""
       |{
       |  "marcTag": "245",
       |  "subfields": [
       |    {"tag": "a", "content": "$title"}
       |  ]
       |}
     """.stripMargin

  def createSierraBibRecordWith(
    id: SierraBibNumber = createSierraBibNumber,
    data: String = "",
    modifiedDate: Instant = olderDate
  ): SierraBibRecord = {

    val recordData = if (data == "") {
      s"""
         |{
         |  "id": "$id",
         |  "varFields": [
         |    ${createTitleVarfield()}
         |  ]
         |}""".stripMargin
    } else data

    SierraBibRecord(
      id = id,
      data = recordData,
      modifiedDate = modifiedDate
    )
  }

  def createSierraBibRecord: SierraBibRecord = createSierraBibRecordWith()

  def createSierraItemRecordWith(
    id: SierraItemNumber = createSierraItemNumber,
    data: (SierraItemNumber, Instant, List[SierraBibNumber]) => String =
      defaultItemData,
    modifiedDate: Instant = Instant.now,
    bibIds: List[SierraBibNumber] = List(),
    unlinkedBibIds: List[SierraBibNumber] = List()
  ): SierraItemRecord = {
    val recordData = data(id, modifiedDate, bibIds)

    SierraItemRecord(
      id = id,
      data = recordData,
      modifiedDate = modifiedDate,
      bibIds = bibIds,
      unlinkedBibIds = unlinkedBibIds
    )
  }

  def createSierraHoldingsRecordWith(
    id: SierraHoldingsNumber = createSierraHoldingsNumber,
    data: (SierraHoldingsNumber, Instant, List[SierraBibNumber]) => String =
      defaultHoldingsData,
    modifiedDate: Instant = Instant.now,
    bibIds: List[SierraBibNumber] = List(),
    unlinkedBibIds: List[SierraBibNumber] = List()
  ): SierraHoldingsRecord = {
    val recordData = data(id, modifiedDate, bibIds)

    SierraHoldingsRecord(
      id = id,
      data = recordData,
      modifiedDate = modifiedDate,
      bibIds = bibIds,
      unlinkedBibIds = unlinkedBibIds
    )
  }

  def createSierraOrderRecordWith(
    id: SierraOrderNumber = createSierraOrderNumber,
    data: (SierraOrderNumber, Instant, List[SierraBibNumber]) => String =
      defaultOrderData,
    modifiedDate: Instant = Instant.now,
    bibIds: List[SierraBibNumber] = List(),
    unlinkedBibIds: List[SierraBibNumber] = List()
  ): SierraOrderRecord = {
    val recordData = data(id, modifiedDate, bibIds)

    SierraOrderRecord(
      id = id,
      data = recordData,
      modifiedDate = modifiedDate,
      bibIds = bibIds,
      unlinkedBibIds = unlinkedBibIds
    )
  }

  def createSierraOrderRecord: SierraOrderRecord =
    createSierraOrderRecordWith()

  private def defaultItemData(
    id: SierraItemNumber,
    modifiedDate: Instant,
    bibIds: List[SierraBibNumber]
  ): String =
    s"""
       |{
       |  "id": "$id",
       |  "updatedDate": "${modifiedDate.toString}",
       |  "bibIds": ${toJson(bibIds.map(_.recordNumber)).get}
       |}
       |""".stripMargin

  private def defaultHoldingsData(
    id: SierraHoldingsNumber,
    modifiedDate: Instant,
    bibIds: List[SierraBibNumber]
  ): String =
    s"""
       |{
       |  "id": $id,
       |  "updatedDate": "${modifiedDate.toString}",
       |  "bibIds": ${toJson(bibIds.map(_.recordNumber.toInt)).get}
       |}
       |""".stripMargin

  private def defaultOrderData(
    id: SierraOrderNumber,
    modifiedDate: Instant,
    bibIds: List[SierraBibNumber]
  ): String = {
    val urls =
      bibIds.map {
        id =>
          s"https://libsys.wellcomelibrary.org/iii/sierra-api/v6/bibs/$id"
      }

    s"""
       |{
       |  "id": $id,
       |  "updatedDate": "${modifiedDate.toString}",
       |  "bibs": ${toJson(urls).get}
       |}
       |""".stripMargin
  }

  def createSierraItemRecord: SierraItemRecord = createSierraItemRecordWith()

  def createSierraTransformableStubWith(
    bibId: SierraBibNumber,
    maybeBibRecord: Option[SierraBibRecord] = None,
    itemRecords: Seq[SierraItemRecord] = List(),
    holdingsRecords: Seq[SierraHoldingsRecord] = List(),
    orderRecords: Seq[SierraOrderRecord] = List()
  ): SierraTransformable =
    SierraTransformable(
      sierraId = bibId,
      maybeBibRecord = maybeBibRecord,
      itemRecords = itemRecords.map {
        record =>
          record.id -> record
      }.toMap,
      holdingsRecords = holdingsRecords.map {
        record =>
          record.id -> record
      }.toMap,
      orderRecords = orderRecords.map {
        record =>
          record.id -> record
      }.toMap,
      modifiedTime = {
        val times =
          (Seq(
            maybeBibRecord
          ).flatten ++ itemRecords ++ holdingsRecords ++ orderRecords)
            .map(_.modifiedDate)

        times match {
          case Nil => Instant.now()
          case _   => times.max
        }
      }
    )

  def createSierraTransformableWith(
    bibRecord: SierraBibRecord,
    itemRecords: Seq[SierraItemRecord] = List(),
    holdingsRecords: Seq[SierraHoldingsRecord] = List(),
    orderRecords: Seq[SierraOrderRecord] = List()
  ): SierraTransformable =
    createSierraTransformableStubWith(
      bibId = bibRecord.id,
      maybeBibRecord = Some(bibRecord),
      itemRecords = itemRecords,
      holdingsRecords = holdingsRecords,
      orderRecords = orderRecords
    )

  def createSierraTransformable: SierraTransformable =
    createSierraTransformableWith(bibRecord = createSierraBibRecord)
}
