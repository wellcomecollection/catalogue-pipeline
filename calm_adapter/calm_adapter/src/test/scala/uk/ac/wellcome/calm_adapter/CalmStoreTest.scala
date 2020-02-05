package uk.ac.wellcome.calm_adapter

import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.{StoreReadError, Version}

class CalmStoreTest extends FunSpec with Matchers {

  type Key = Version[String, Int]
  type Data = Map[String, String]

  it("stores new CALM records") {
    val data = dataStore()
    val record = CalmRecord("A", Map("key" -> "value"))
    calmStore(data).putRecord(record) shouldBe Right(Some(Version("A", 0)))
    data.entries shouldBe Map(
      Version("A", 0) ->  Map("key" -> "value")
    )
  }

  it("stores already seen CALM records when the data has changed") {
    val data = dataStore(Map(Version("A", 1) -> Map("key" -> "old")))
    val record = CalmRecord("A", Map("key" -> "new"))
    calmStore(data).putRecord(record) shouldBe Right(Some(Version("A", 2)))
    data.entries shouldBe Map(
      Version("A", 1) ->  Map("key" -> "old"),
      Version("A", 2) ->  Map("key" -> "new")
    )
  }

  it("doesn't store already seen CALM records when unchanged data") {
    val data = dataStore(Map(Version("A", 4) -> Map("key" -> "new")))
    val record = CalmRecord("A", Map("key" -> "new"))
    calmStore(data).putRecord(record) shouldBe Right(None)
    data.entries shouldBe Map(
      Version("A", 4) ->  Map("key" -> "new"),
    )
  }

  it("doesn't store CALM records when checking the stored data fails") {
    val data = dataStore()
    val record = CalmRecord("A", Map("key" -> "value"))
    val calmStore = new CalmStore(
      new MemoryVersionedStore(data) {
        override def getLatest(id: String): ReadEither =
          Left(StoreReadError(new Exception("Not today mate")))
      }
    )
    calmStore.putRecord(record) shouldBe a[Left[_, _]]
    data.entries shouldBe Map.empty
  }

  def dataStore(
    entries: Map[Key, Data] = Map.empty) =
    new MemoryStore(entries) with MemoryMaxima[String, Data]

  def calmStore(data: MemoryStore[Key, Data] with Maxima[String, Int]) =
    new CalmStore(new MemoryVersionedStore(data))
}
