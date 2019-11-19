package uk.ac.wellcome.mets_adapter.services

import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.mets_adapter.models.MetsData
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, Version}

class MetsStoreTest extends FunSpec with Matchers {

  it("should store new METS data") {
    val internalStore = createInternalStore()
    val store = new MetsStore(internalStore)
    val data = MetsData("mets/file.xml", 3)
    store.storeMetsData("001", data) shouldBe Right(data)
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 0), data)
    )
  }

  it("should not filter update METS data when newer version") {
    val oldData = MetsData("old/file.xml", 1)
    val newData = MetsData("new/file.xml", 2)
    val internalStore = createInternalStore(
      Map(Version("001", 0) -> oldData)
    )
    val store = new MetsStore(internalStore)
    store.filterMetsData("001", newData) shouldBe Right(Some(newData))
  }

  it("should filter METS data when older version") {
    val oldData = MetsData("old/file.xml", 2)
    val newData = MetsData("new/file.xml", 1)
    val internalStore = createInternalStore(
      Map(Version("001", 0) -> oldData)
    )
    val store = new MetsStore(internalStore)
    store.filterMetsData("001", newData) shouldBe Right(None)
  }

  it("should filter METS data when file path unchanged") {
    val oldData = MetsData("old/file.xml", 2)
    val newData = MetsData("old/file.xml", 1)
    val internalStore = createInternalStore(
      Map(Version("001", 0) -> oldData)
    )
    val store = new MetsStore(internalStore)
    store.filterMetsData("001", newData) shouldBe Right(None)
  }

  def createInternalStore(
    data: Map[Version[String, Int], MetsData] = Map.empty) =
    MemoryVersionedStore(data)
}
