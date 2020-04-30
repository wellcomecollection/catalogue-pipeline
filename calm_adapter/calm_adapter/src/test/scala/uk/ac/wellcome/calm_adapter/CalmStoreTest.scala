package uk.ac.wellcome.calm_adapter

import org.scalatest.matchers.should.Matchers
import java.time.Instant

import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.{StoreReadError, Version}

class CalmStoreTest extends AnyFunSpec with Matchers {

  type Key = Version[String, Int]

  val retrievedAt = Instant.ofEpochSecond(123456)
  val oldData = Map("key" -> List("old"))
  val newData = Map("key" -> List("new"))

  describe("putRecord") {
    it("stores new CALM records") {
      val data = dataStore()
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)
      calmStore(data).putRecord(record) shouldBe Right(
        Some(Version("A", 0) -> record))
      data.entries shouldBe Map(Version("A", 0) -> record)
    }

    it(
      "replaces a stored CALM record if the retrieval date is newer and the data differs") {
      val oldTime = retrievedAt
      val newTime = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)
      val oldRecord = CalmRecord("A", oldData, oldTime, published = true)
      val newRecord = CalmRecord("A", newData, newTime)
      val data = dataStore(Version("A", 1) -> oldRecord)
      calmStore(data).putRecord(newRecord) shouldBe Right(
        Some(Version("A", 2) -> newRecord))
      data.entries shouldBe Map(
        Version("A", 1) -> oldRecord,
        Version("A", 2) -> newRecord
      )
    }

    it(
      "does not replace a stored CALM record if the retrieval date is newer and the data is the same") {
      val oldTime = retrievedAt
      val newTime = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)
      val oldRecord = CalmRecord("A", oldData, oldTime, published = true)
      val newRecord = CalmRecord("A", oldData, newTime)
      val data = dataStore(Version("A", 1) -> oldRecord)
      calmStore(data).putRecord(newRecord) shouldBe Right(None)
      data.entries shouldBe Map(Version("A", 1) -> oldRecord)
    }

    it(
      "replaces a stored CALM record if the data is the same but it is not recorded as published") {
      val oldTime = retrievedAt
      val newTime = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)
      val oldRecord = CalmRecord("A", oldData, oldTime, published = false)
      val newRecord = CalmRecord("A", oldData, newTime)
      val data = dataStore(Version("A", 1) -> oldRecord)
      calmStore(data).putRecord(newRecord) shouldBe Right(
        Some(Version("A", 2) -> newRecord))
      data.entries shouldBe Map(
        Version("A", 1) -> oldRecord,
        Version("A", 2) -> newRecord
      )
    }

    it(
      "does not replace a stored CALM record if the retrieval date on the new record is older") {
      val oldTime = retrievedAt
      val newTime = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)
      val oldRecord = CalmRecord("A", oldData, oldTime)
      val newRecord = CalmRecord("A", newData, newTime)
      val data = dataStore(Version("A", 4) -> newRecord)
      calmStore(data).putRecord(oldRecord) shouldBe Right(None)
      data.entries shouldBe Map(Version("A", 4) -> newRecord)
    }

    it("doesn't store CALM records when checking the stored data fails") {
      val data = dataStore()
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)
      val calmStore = new CalmStore(
        new MemoryVersionedStore(data) {
          override def getLatest(id: String): ReadEither =
            Left(StoreReadError(new Exception("Not today mate")))
        }
      )
      calmStore.putRecord(record) shouldBe a[Left[_, _]]
      data.entries shouldBe Map.empty
    }

    it("errors if the data differs but timestamp is the same") {
      val x = CalmRecord("A", Map("key" -> List("x")), retrievedAt)
      val y = CalmRecord("A", Map("key" -> List("y")), retrievedAt)
      val data = dataStore(Version("A", 2) -> x)
      calmStore(data).putRecord(y) shouldBe a[Left[_, _]]
      data.entries shouldBe Map(Version("A", 2) -> x)
    }
  }

  describe("setRecordPublished") {
    it("sets Calm records as published") {
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)
      val data = dataStore(Version("A", 5) -> record)
      calmStore(data).setRecordPublished(Version("A", 5), record) shouldBe
        Right(Version("A", 6))
      data.entries shouldBe Map(
        Version("A", 5) -> record,
        Version("A", 6) -> record.copy(published = true),
      )
    }

    it("fails setting Calm record as published if version already exists") {
      val record = CalmRecord("A", Map("key" -> List("value")), retrievedAt)
      val data = dataStore(
        Version("A", 5) -> record,
        Version("A", 6) -> record.copy(data = newData)
      )
      val result = calmStore(data).setRecordPublished(Version("A", 5), record)
      result shouldBe a[Left[_, _]]
      result.left.get.getMessage shouldBe "VersionAlreadyExistsError"
      data.entries shouldBe Map(
        Version("A", 5) -> record,
        Version("A", 6) -> record.copy(data = newData)
      )
    }
  }

  def dataStore(entries: (Key, CalmRecord)*) =
    new MemoryStore(entries.toMap) with MemoryMaxima[String, CalmRecord]

  def calmStore(data: MemoryStore[Key, CalmRecord] with Maxima[String, Int]) =
    new CalmStore(new MemoryVersionedStore(data))
}
