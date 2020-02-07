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
    calmStore(data).putRecord(record) shouldBe Right(Some(record))
    data.entries shouldBe Map(
      Version("A", 0) -> Map("key" -> "value")
    )
  }

  it("stores already seen CALM records when the data has changed") {
    val data = dataStore(
      Version("A", 1) -> CalmRecord("A", Map("key" -> "old"), retrievedAt)
    )
    val record = CalmRecord("A", Map("key" -> "new"), retrievedAt)
    calmStore(data).putRecord(record) shouldBe Right(Some(record))
    data.entries shouldBe Map(
      Version("A", 1) -> Map("key" -> "old"),
      Version("A", 2) -> Map("key" -> "new")
    )
  }

  it("doesn't store already seen CALM records when unchanged data") {
    val data = dataStore(
      Version("A", 4) -> CalmRecord("A", Map("key" -> "new"), retrievedAt)
    )
    val record = CalmRecord("A", Map("key" -> "new"), retrievedAt)
    calmStore(data).putRecord(record) shouldBe Right(None)
    data.entries shouldBe Map(
      Version("A", 4) -> Map("key" -> "new"),
    )
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

  def dataStore(entries: (Key, CalmRecord)*) =
    new MemoryStore(entries.toMap) with MemoryMaxima[String, CalmRecord]

  def calmStore(data: MemoryStore[Key, CalmRecord] with Maxima[String, Int]) =
    new CalmStore(new MemoryVersionedStore(data))
}
