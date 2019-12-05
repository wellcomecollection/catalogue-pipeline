package uk.ac.wellcome.mets_adapter.services

import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.mets_adapter.models.MetsData

class MetsStoreTest extends FunSpec with Matchers {

  it("should store new METS data") {
    val internalStore = createInternalStore()
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsData("NEW")) shouldBe Right(Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), metsData("NEW"))
    )
  }

  it("should update METS data when newer version") {
    val internalStore = createInternalStore(Version("001", 1) -> "OLD")
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 2), metsData("NEW")) shouldBe Right(Version("001", 2))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), metsData("NEW"))
    )
  }

  it("should not update METS data when current version") {
    val internalStore = createInternalStore(Version("001", 1) -> "OLD")
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsData("NEW")) shouldBe Right(Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), metsData("OLD"))
    )
  }

  it("should error when inserting older version") {
    val internalStore = createInternalStore(Version("001", 2) -> "NEW")
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsData("NEW")) shouldBe a[Left[_, _]]
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), metsData("NEW"))
    )
  }

  def createInternalStore(data: (Version[String, Int], String)*) =
    MemoryVersionedStore[String, MetsData](
      Map(
        data.map { case (version, file) => (version, metsData(file)) }: _*
      )
    )

  def metsData(file: String = "NEW", version: Int = 1) =
    MetsData("bucket", "path", version, file, Nil)
}
