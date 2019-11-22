package uk.ac.wellcome.mets_adapter.services

import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.storage.store.HybridStoreEntry

class MetsStoreTest extends FunSpec with Matchers {

  it("should store new METS data") {
    val internalStore = createInternalStore()
    val store = new MetsStore(internalStore)
    store.storeXml(Version("001", 1), "NEW") shouldBe Right(Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), vhsEntry("NEW"))
    )
  }

  it("should update METS data when newer version") {
    val internalStore = createInternalStore(Version("001", 1) -> "OLD")
    val store = new MetsStore(internalStore)
    store.storeXml(Version("001", 2), "NEW") shouldBe Right(Version("001", 2))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), vhsEntry("NEW"))
    )
  }

  it("should not update METS data when current version") {
    val internalStore = createInternalStore(Version("001", 1) -> "OLD")
    val store = new MetsStore(internalStore)
    store.storeXml(Version("001", 1), "NEW") shouldBe Right(Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), vhsEntry("OLD"))
    )
  }

  it("should error when inserting older version") {
    val internalStore = createInternalStore(Version("001", 2) -> "NEW")
    val store = new MetsStore(internalStore)
    store.storeXml(Version("001", 1), "NEW") shouldBe a[Left[_, _]]
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), vhsEntry("NEW"))
    )
  }

  def createInternalStore(data: (Version[String, Int], String)*) =
    MemoryVersionedStore[String, HybridStoreEntry[String, EmptyMetadata]](
      Map(
        data.map { case (version, xml) => (version, vhsEntry(xml)) }: _*
      )
    )

  def vhsEntry(xml: String) =
    HybridStoreEntry(xml, EmptyMetadata())
}
