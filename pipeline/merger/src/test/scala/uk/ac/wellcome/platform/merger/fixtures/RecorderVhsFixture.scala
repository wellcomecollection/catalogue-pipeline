package uk.ac.wellcome.platform.merger.fixtures

import org.scalatest.{Assertion, EitherValues, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.memory.{MemoryObjectStore, MemoryVersionedDao}
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}
import uk.ac.wellcome.storage.streaming.CodecInstances._

trait RecorderVhsFixture extends EitherValues with Matchers {
  type RecorderDao = MemoryVersionedDao[String, Entry[String, EmptyMetadata]]
  type RecorderStore = MemoryObjectStore[TransformedBaseWork]
  type RecorderVhs =
    VersionedHybridStore[String, TransformedBaseWork, EmptyMetadata]

  def createDao: RecorderDao =
    MemoryVersionedDao[String, Entry[String, EmptyMetadata]]()
  def createStore: RecorderStore = new RecorderStore()
  def createVhs(dao: RecorderDao = createDao,
                store: RecorderStore = createStore): RecorderVhs =
    new RecorderVhs {
      override protected val versionedDao: RecorderDao = dao
      override protected val objectStore: RecorderStore = store
    }

  def storeInVhs(vhs: RecorderVhs,
                 works: TransformedBaseWork*): Seq[Assertion] =
    works.map { work =>
      val result = vhs.update(work.sourceIdentifier.toString)(
        ifNotExisting = (work, EmptyMetadata()))((_, _) =>
        throw new RuntimeException("Not possible, VHS is empty!"))

      result.right.value shouldBe Unit
    }
}
