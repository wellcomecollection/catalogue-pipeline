package uk.ac.wellcome.platform.calm_deletion_checker

import org.scalatest.{EitherValues, OptionValues, TryValues}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.{DynamoFormat, Table => ScanamoTable}
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import weco.catalogue.source_model.CalmSourcePayload

import scala.language.higherKinds

case class CalmSourcePayloadWithoutDeletionFlag(
  id: String,
  version: Int,
  location: S3ObjectLocation
) {
  def toCalmSourcePayload = CalmSourcePayload(
    id = id,
    version = version,
    location = location,
  )
}

class DeletionMarkerTest
    extends AnyFunSpec
    with Matchers
    with TryValues
    with OptionValues
    with EitherValues
    with DynamoFixtures
    with CalmSourcePayloadGenerators {

  it("marks a record with isDeleted = false as deleted") {
    val records = Seq.fill(5)(calmSourcePayload)
    withExistingRecords(records) {
      case (deletionMarker, table) =>
        val targetRecord = records.head
        val result = deletionMarker(targetRecord)

        result.success.value shouldBe targetRecord.copy(isDeleted = true)
        getRecordFromTable(targetRecord.id, targetRecord.version, table) shouldEqual result.success.value
    }
  }

  it("marks a record with no isDeleted attribute as deleted") {
    val records = Seq.fill(5)(calmSourcePayload).map {
      case CalmSourcePayload(id, location, version, _) =>
        CalmSourcePayloadWithoutDeletionFlag(id, version, location)
    }
    withExistingRecords(records) {
      case (deletionMarker, table) =>
        val targetRecord = records.head.toCalmSourcePayload
        val result = deletionMarker(targetRecord)

        result.success.value shouldBe targetRecord.copy(isDeleted = true)
        getRecordFromTable(targetRecord.id, targetRecord.version, table) shouldEqual result.success.value
    }
  }

  it("fails if the item is already marked as deleted") {
    val records = calmSourcePayloadWith(isDeleted = true) +:
      Seq.fill(4)(calmSourcePayload)
    withExistingRecords(records) {
      case (deletionMarker, _) =>
        val targetRecord = records.head
        val result = deletionMarker(targetRecord)

        result.failure.exception shouldBe a[ConditionalCheckFailedException]
    }
  }

  it("fails if the item does not exist") {
    withExistingRecords(Seq.fill(5)(calmSourcePayload)) {
      case (deletionMarker, _) =>
        val anotherRecord = calmSourcePayload
        val result = deletionMarker(anotherRecord)

        result.failure.exception shouldBe a[RuntimeException]
    }
  }

  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashRangeKey(table)

  def withExistingRecords[T: DynamoFormat, R](records: Seq[T])(
    testWith: TestWith[(DeletionMarker, Table), R]): R =
    withLocalDynamoDbTable { table =>
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
        ScanamoTable[CalmSourcePayload](table.name)
          .get("id" === id and "version" === version)
      )
      .value
      .value
}
