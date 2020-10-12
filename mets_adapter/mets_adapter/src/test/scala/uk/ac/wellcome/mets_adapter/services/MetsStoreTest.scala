package uk.ac.wellcome.mets_adapter.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import java.time.Instant

class MetsStoreTest extends AnyFunSpec with Matchers {

  it("should store new METS data") {
    val createdDate = Instant.now
    val internalStore = createInternalStore()
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsLocation("NEW", createdDate)) shouldBe Right(
      Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), metsLocation("NEW", createdDate))
    )
  }

  it("should update METS data when newer version") {
    val createdDate = Instant.now
    val internalStore = createInternalStore(Version("001", 1) -> (("OLD", createdDate)))
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 2), metsLocation(file = "NEW", createdDate = createdDate)) shouldBe Right(
      Version("001", 2))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), metsLocation("NEW", createdDate))
    )
  }

  it("should not update METS data when current version") {
    val createdDate = Instant.now
    val internalStore = createInternalStore(Version("001", 1) -> (("OLD", createdDate)))
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsLocation("NEW", Instant.now)) shouldBe Right(
      Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), metsLocation("OLD", createdDate))
    )
  }

  it("should error when inserting older version") {
    val createdDate = Instant.now
    val internalStore = createInternalStore(Version("001", 2) -> (("NEW", createdDate)))
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsLocation("NEW", createdDate)) shouldBe a[Left[_,
                                                                            _]]
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), metsLocation("NEW", createdDate))
    )
  }

  def createInternalStore(data: (Version[String, Int], (String, Instant))*) =
    MemoryVersionedStore[String, MetsLocation](
      Map(
        data.map { case (version, (file, createdDate)) => (version, metsLocation(file, createdDate = createdDate)) }: _*
      )
    )

  def metsLocation(file: String, createdDate: Instant, version: Int = 1) =
    MetsLocation("bucket", "path", version, file, createdDate,Nil)
}
