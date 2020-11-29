package uk.ac.wellcome.platform.transformer.miro.services

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.{Table => ScanamoTable}
import org.scanamo.auto._
import uk.ac.wellcome.platform.transformer.miro.models.MiroVHSRecord
import uk.ac.wellcome.storage.{DoesNotExistError, Identified, StoreReadError}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.s3.S3ObjectLocation

class MiroDynamoVHSReaderTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with DynamoFixtures {
  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S)

  it("finds a Miro VHS record") {
    val vhsRecord = MiroVHSRecord(
      id = "C0041442",
      isClearedForCatalogueAPI = false,
      location = S3ObjectLocation(
        bucket = "wellcomecollection-vhs-sourcedata-miro",
        key =
          "C0041442/318323f7c3a9ac93ce33a8179821f5e2c442d78f951b273c24f500a0ef35f093.json"
      ),
      version = 1
    )

    withLocalDynamoDbTable { table =>
      scanamo.exec(ScanamoTable[MiroVHSRecord](table.name).put(vhsRecord))

      val reader = new MiroDynamoVHSReader(createDynamoConfigWith(table))

      reader.get("C0041442").value shouldBe Identified("C0041442", vhsRecord)
    }
  }

  it("returns a Left[DoesNotExistError] for a missing ID") {
    withLocalDynamoDbTable { table =>
      val reader = new MiroDynamoVHSReader(createDynamoConfigWith(table))

      reader.get("doesnotexist").left.value shouldBe a[DoesNotExistError]
    }
  }

  it("returns a Left[StoreReadError] if it can't parse the value in the table") {
    withLocalDynamoDbTable { table =>
      case class BadRecord(id: String, version: String)
      val badRecord = BadRecord(id = "V0001234", version = "one")

      scanamo.exec(ScanamoTable[BadRecord](table.name).put(badRecord))

      val reader =
        new MiroDynamoVHSReader(createDynamoConfigWith(nonExistentTable))

      reader.get("V0001234").left.value shouldBe a[StoreReadError]
    }
  }

  it("returns a Left[StoreReadError] for a missing ID") {
    val reader =
      new MiroDynamoVHSReader(createDynamoConfigWith(nonExistentTable))

    reader.get("doesnotexist").left.value shouldBe a[StoreReadError]
  }
}
