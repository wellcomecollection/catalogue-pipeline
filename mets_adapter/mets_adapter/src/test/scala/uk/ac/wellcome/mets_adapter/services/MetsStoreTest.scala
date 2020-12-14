package uk.ac.wellcome.mets_adapter.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import weco.catalogue.source_model.generators.MetsSourceDataGenerators
import weco.catalogue.source_model.mets.MetsSourceData

class MetsStoreTest
    extends AnyFunSpec
    with Matchers
    with MetsSourceDataGenerators {

  it("stores new METS data") {
    val id = Version("b1234", version = 1)
    val data = createMetsSourceData

    val internalStore = MemoryVersionedStore[String, MetsSourceData](
      initialEntries = Map.empty
    )
    val store = new MetsStore(internalStore)

    store.storeData(id, data) shouldBe Right(id)
    internalStore.getLatest(id.id) shouldBe Right(Identified(id, data))
  }

  it("updates the METS data when it gets a newer version") {
    val id = "b1234"

    val oldId = Version(id, version = 1)
    val newId = Version(id, version = 2)

    val oldData = createMetsSourceDataWith(createdDate = olderDate)
    val newData = createMetsSourceDataWith(createdDate = newerDate)

    val internalStore = MemoryVersionedStore[String, MetsSourceData](
      initialEntries = Map(oldId -> oldData)
    )
    val store = new MetsStore(internalStore)

    store.storeData(newId, newData) shouldBe Right(newId)
    internalStore.getLatest(id) shouldBe Right(Identified(newId, newData))
  }

  it("doesn't update the METS data if it's already up-to-date") {
    val id = Version("b1234", version = 1)
    val data = createMetsSourceData

    val internalStore = MemoryVersionedStore[String, MetsSourceData](
      initialEntries = Map(id -> data)
    )
    val store = new MetsStore(internalStore)

    store.storeData(id, data) shouldBe Right(id)
    internalStore.getLatest(id.id) shouldBe Right(Identified(id, data))
  }

  it("errors if you try to insert an older version") {
    val id = "b1234"

    val oldId = Version(id, version = 1)
    val newId = Version(id, version = 2)

    val oldData = createMetsSourceDataWith(createdDate = olderDate)
    val newData = createMetsSourceDataWith(createdDate = newerDate)

    val internalStore = MemoryVersionedStore[String, MetsSourceData](
      initialEntries = Map(newId -> newData)
    )
    val store = new MetsStore(internalStore)

    store.storeData(oldId, oldData) shouldBe a[Left[_, _]]
    internalStore.getLatest(id) shouldBe Right(Identified(newId, newData))
  }
}
