package weco.pipeline.calm_adapter

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, EitherValues}
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.{Identified, Version}
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS
import weco.catalogue.source_model.Implicits._
import java.time.Instant

class CalmStoreTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with SourceVHSFixture {

  type Key = Version[String, Int]

  val retrievedAt: Instant = Instant.ofEpochSecond(123456)

  val oldTime: Instant = retrievedAt
  val newTime: Instant = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)

  val data = Map("key" -> List("data"))
  val oldData = Map("key" -> List("old"))
  val newData = Map("key" -> List("new"))

  describe("putRecord") {
    it("stores new CALM records") {
      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHS[CalmRecord]
      val calmStore = new CalmStore(sourceVHS)

      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)

      val (storedId, storedLocation, storedRecord) =
        calmStore.putRecord(record).value.get

      storedId shouldBe Version("A", 0)

      assertStored(
        id = "A",
        expectedVersion = 0,
        expectedRecord = record,
        storedRecord = storedRecord,
        storedLocation = storedLocation
      )
    }

    it("replaces a stored record if the data is newer and different") {
      val oldRecord =
        CalmRecord("A", oldData, oldTime, published = true)
      val newRecord = CalmRecord("A", newData, newTime)

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 1) -> oldRecord)
        )

      val calmStore = new CalmStore(sourceVHS)

      val (storedId, storedLocation, storedRecord) =
        calmStore.putRecord(newRecord).value.get

      storedId shouldBe Version("A", 2)

      assertStored(
        id = "A",
        expectedVersion = 2,
        expectedRecord = newRecord,
        storedRecord = storedRecord,
        storedLocation = storedLocation
      )
    }

    it(
      "does not replace a stored CALM record if the retrieval date is newer and the data is the same") {
      val oldRecord =
        CalmRecord("A", data, oldTime, published = true)
      val newRecord = CalmRecord("A", data, newTime)

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 1) -> oldRecord)
        )

      val calmStore = new CalmStore(sourceVHS)

      calmStore.putRecord(newRecord).value shouldBe None

      assertStored(id = "A", expectedVersion = 1, expectedRecord = oldRecord)
    }

    it(
      "replaces a stored CALM record if the data is the same but it is not recorded as published") {
      val oldRecord =
        CalmRecord("A", oldData, oldTime, published = false)
      val newRecord = CalmRecord("A", oldData, newTime)

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 1) -> oldRecord)
        )

      val calmStore = new CalmStore(sourceVHS)

      val (storedId, storedLocation, storedRecord) =
        calmStore.putRecord(newRecord).value.get

      storedId shouldBe Version("A", 2)

      assertStored(
        id = "A",
        expectedVersion = 2,
        expectedRecord = newRecord,
        storedRecord = storedRecord,
        storedLocation = storedLocation
      )
    }

    it(
      "does not replace a stored CALM record if the retrieval date on the new record is older") {
      val oldRecord = CalmRecord("A", oldData, oldTime)
      val newRecord = CalmRecord("A", newData, newTime)

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 4) -> newRecord)
        )

      val calmStore = new CalmStore(sourceVHS)

      calmStore.putRecord(oldRecord).value shouldBe None

      assertStored(id = "A", expectedVersion = 4, expectedRecord = newRecord)
    }

    it("doesn't store CALM records when checking the stored data fails") {
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)

      val calmStore = new CalmStore(createSourceVHS[CalmRecord]) {
        override def shouldStoreRecord(record: CalmRecord): Result[Boolean] =
          Left(new Throwable("BOOM!"))
      }

      calmStore.putRecord(record) shouldBe a[Left[_, _]]
    }

    it("errors if the data differs but timestamp is the same") {
      val x =
        CalmRecord("A", Map("key" -> List("x")), retrievedAt)
      val y =
        CalmRecord("A", Map("key" -> List("y")), retrievedAt)

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 2) -> x)
        )

      val calmStore = new CalmStore(sourceVHS)

      calmStore.putRecord(y) shouldBe a[Left[_, _]]

      assertStored(id = "A", expectedVersion = 2, expectedRecord = x)
    }
  }

  describe("setRecordPublished") {
    it("sets Calm records as published") {
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)
      record.published shouldBe false

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 5) -> record)
        )

      val calmStore = new CalmStore(sourceVHS)

      calmStore.setRecordPublished(Version("A", 5), record) shouldBe a[Right[_,
                                                                             _]]

      assertStored(
        id = "A",
        expectedVersion = 6,
        expectedRecord = record.copy(published = true))
    }

    it("fails setting Calm record as published if version already exists") {
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)
      record.published shouldBe false

      implicit val sourceVHS: SourceVHS[CalmRecord] =
        createSourceVHSWith(
          initialEntries = Map(Version("A", 6) -> record)
        )

      val calmStore = new CalmStore(sourceVHS)

      val err = calmStore.setRecordPublished(Version("A", 5), record).left.value
      err.getMessage shouldBe "Tried to store A at version 6, but that version already exists"

      assertStored(id = "A", expectedVersion = 6, expectedRecord = record)
    }
  }

  private def assertStored(
    id: String,
    storedLocation: S3ObjectLocation,
    storedRecord: CalmRecord,
    expectedVersion: Int,
    expectedRecord: CalmRecord,
  )(
    implicit sourceVHS: SourceVHS[CalmRecord]
  ): Assertion = {
    storedRecord shouldBe expectedRecord

    sourceVHS.underlying.hybridStore.typedStore
      .get(storedLocation)
      .value
      .identifiedT shouldBe expectedRecord

    assertStored(id, expectedVersion, expectedRecord)
  }

  private def assertStored(
    id: String,
    expectedVersion: Int,
    expectedRecord: CalmRecord,
  )(
    implicit sourceVHS: SourceVHS[CalmRecord]
  ): Assertion =
    sourceVHS.underlying.getLatest(id).value shouldBe Identified(
      Version(id, expectedVersion),
      expectedRecord)
}
