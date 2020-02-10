package uk.ac.wellcome.calm_adapter

import org.scalatest.{FunSpec, Matchers}
import java.time.Instant

import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.{StoreReadError, Version}

class CalmStoreTest extends FunSpec with Matchers {

  type Key = Version[String, Int]

  val retrievedAt = Instant.ofEpochSecond(123456)

  it("stores new CALM records") {
    val data = dataStore()
    val record = CalmRecord("A", Map("key" -> "value"), retrievedAt)
    calmStore(data).putRecord(record) shouldBe Right(Some(Version("A", 0)))
    data.entries shouldBe Map(Version("A", 0) -> record)
  }

  it("replaces a previously stored CALM record if the retrieval date is newer") {
    val oldTime = retrievedAt
    val newTime = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)
    val oldRecord = CalmRecord("A", Map("key" -> "old"), oldTime)
    val newRecord = CalmRecord("A", Map("key" -> "new"), newTime)
    val data = dataStore(Version("A", 1) -> oldRecord)
    calmStore(data).putRecord(newRecord) shouldBe Right(Some(Version("A", 2)))
    data.entries shouldBe Map(
      Version("A", 1) -> oldRecord,
      Version("A", 2) -> newRecord
    )
  }

  it(
    "does not replace a stored CALM record if the retrieval date on the new record is older") {
    val oldTime = retrievedAt
    val newTime = Instant.ofEpochSecond(retrievedAt.getEpochSecond + 2)
    val oldRecord = CalmRecord("A", Map("key" -> "old"), oldTime)
    val newRecord = CalmRecord("A", Map("key" -> "old"), newTime)
    val data = dataStore(Version("A", 4) -> newRecord)
    calmStore(data).putRecord(oldRecord) shouldBe Right(None)
    data.entries shouldBe Map(Version("A", 4) -> newRecord)
  }

  it("doesn't store CALM records when checking the stored data fails") {
    val data = dataStore()
    val record = CalmRecord("A", Map("key" -> "value"), retrievedAt)
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
    val x = CalmRecord("A", Map("key" -> "x"), retrievedAt)
    val y = CalmRecord("A", Map("key" -> "y"), retrievedAt)
    val data = dataStore(Version("A", 2) -> x)
    calmStore(data).putRecord(y) shouldBe a[Left[_, _]]
    data.entries shouldBe Map(Version("A", 2) -> x)
  }

  def dataStore(entries: (Key, CalmRecord)*) =
    new MemoryStore(entries.toMap) with MemoryMaxima[String, CalmRecord]

  def calmStore(data: MemoryStore[Key, CalmRecord] with Maxima[String, Int]) =
    new CalmStore(new MemoryVersionedStore(data))
}
