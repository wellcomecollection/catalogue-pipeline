package uk.ac.wellcome.mets_adapter.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.mets_adapter.models.MetsSourceData
import java.time.Instant

class MetsStoreTest extends AnyFunSpec with Matchers {

  it("should store new METS data") {
    val createdDate = Instant.now
    val internalStore = createInternalStore()
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsSourceData("NEW", createdDate)) shouldBe Right(
      Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), metsSourceData("NEW", createdDate))
    )
  }

  it("should update METS data when newer version") {
    val createdDate = Instant.now
    val internalStore =
      createInternalStore(Version("001", 1) -> (("OLD", createdDate)))
    val store = new MetsStore(internalStore)
    store.storeData(
      Version("001", 2),
      metsSourceData(file = "NEW", createdDate = createdDate)) shouldBe Right(
      Version("001", 2))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), metsSourceData("NEW", createdDate))
    )
  }

  it("should not update METS data when current version") {
    val createdDate = Instant.now
    val internalStore =
      createInternalStore(Version("001", 1) -> (("OLD", createdDate)))
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsSourceData("NEW", Instant.now)) shouldBe Right(
      Version("001", 1))
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 1), metsSourceData("OLD", createdDate))
    )
  }

  it("should error when inserting older version") {
    val createdDate = Instant.now
    val internalStore =
      createInternalStore(Version("001", 2) -> (("NEW", createdDate)))
    val store = new MetsStore(internalStore)
    store.storeData(Version("001", 1), metsSourceData("NEW", createdDate)) shouldBe a[
      Left[_, _]]
    internalStore.getLatest("001") shouldBe Right(
      Identified(Version("001", 2), metsSourceData("NEW", createdDate))
    )
  }

  def createInternalStore(data: (Version[String, Int], (String, Instant))*) =
    MemoryVersionedStore[String, MetsSourceData](
      Map(
        data.map {
          case (version, (file, createdDate)) =>
            (version, metsSourceData(file, createdDate = createdDate))
        }: _*
      )
    )

  def metsSourceData(file: String, createdDate: Instant, version: Int = 1) =
    MetsSourceData(
      bucket = "bucket",
      path = "path",
      version = version,
      file = file,
      createdDate = createdDate,
      deleted = false,
      manifestations = Nil)
}
