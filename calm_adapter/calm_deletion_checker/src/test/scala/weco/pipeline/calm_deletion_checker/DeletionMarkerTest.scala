package weco.pipeline.calm_deletion_checker

import org.scalatest.{EitherValues, OptionValues, TryValues}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.{DynamoFormat, Table => ScanamoTable}
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import weco.fixtures.TestWith
import weco.storage.fixtures.DynamoFixtures
import weco.storage.fixtures.DynamoFixtures.Table
import weco.catalogue.source_model.CalmSourcePayload
import weco.pipeline.calm_deletion_checker.fixtures.CalmSourcePayloadGenerators

import scala.language.higherKinds

class DeletionMarkerTest
    extends AnyFunSpec
    with Matchers
    with TryValues
    with OptionValues
    with EitherValues
    with DynamoFixtures
    with CalmSourcePayloadGenerators {

  it("marks a record with isDeleted = false as deleted") {
    val rows = Seq.fill(5)(calmSourcePayload).map(_.toDynamoRow)
    withExistingRecords(rows) {
      case (deletionMarker, table) =>
        val targetRecord = rows.head.toPayload
        val result = deletionMarker(targetRecord)

        result.success.value shouldBe targetRecord.copy(
          isDeleted = true,
          version = targetRecord.version + 1
        )
        getRecordFromTable(
          targetRecord.id,
          targetRecord.version,
          table
        ) shouldEqual result.success.value
    }
  }

  it("marks a record with no isDeleted attribute as deleted") {
    val rows =
      Seq.fill(5)(calmSourcePayload).map(_.toDynamoRowWithoutDeletionFlag)
    withExistingRecords(rows) {
      case (deletionMarker, table) =>
        val targetRecord = rows.head.toPayload
        val result = deletionMarker(targetRecord)

        result.success.value shouldBe targetRecord.copy(
          isDeleted = true,
          version = targetRecord.version + 1
        )
        getRecordFromTable(
          targetRecord.id,
          targetRecord.version,
          table
        ) shouldEqual result.success.value
    }
  }

  it("succeeds if the item is already marked as deleted") {
    val records = (calmSourcePayloadWith(isDeleted = true) +:
      Seq.fill(4)(calmSourcePayload)).map(_.toDynamoRow)
    withExistingRecords(records) {
      case (deletionMarker, table) =>
        val targetRecord = records.head.toPayload
        val result = deletionMarker(targetRecord)

        result.success.value shouldBe targetRecord.copy(
          isDeleted = true,
          version = targetRecord.version + 1
        )
        getRecordFromTable(
          targetRecord.id,
          targetRecord.version,
          table
        ) shouldEqual result.success.value
    }
  }

  it("fails if the item does not exist") {
    withExistingRecords(Seq.fill(5)(calmSourcePayload).map(_.toDynamoRow)) {
      case (deletionMarker, _) =>
        val anotherRecord = calmSourcePayload
        val result = deletionMarker(anotherRecord)

        result.failure.exception shouldBe a[ConditionalCheckFailedException]
    }
  }

  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashKey(table)

  def withExistingRecords[T: DynamoFormat, R](
    records: Seq[T]
  )(testWith: TestWith[(DeletionMarker, Table), R]): R =
    withLocalDynamoDbTable {
      table =>
        putTableItems(records, table)
        testWith((new DeletionMarker(table.name), table))
    }

  def getRecordFromTable(
    id: String,
    version: Int,
    table: Table
  ): CalmSourcePayload =
    scanamo
      .exec(
        ScanamoTable[CalmSourceDynamoRow](table.name)
          .get("id" === id)
      )
      .value
      .value
      .toPayload
}
