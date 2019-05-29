package uk.ac.wellcome.sierra_adapter.utils

import org.scalatest.{Assertion, EitherValues}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.storage.memory.{MemoryObjectStore, MemoryVersionedDao}
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}

trait SierraAdapterHelpers extends Messaging with EitherValues {
  type SierraDao = MemoryVersionedDao[String, Entry[String, EmptyMetadata]]
  type SierraStore = MemoryObjectStore[SierraTransformable]
  type SierraVHS = VersionedHybridStore[String, SierraTransformable, EmptyMetadata]

  def createDao: SierraDao = MemoryVersionedDao[String, Entry[String, EmptyMetadata]]()
  def createStore: SierraStore = new SierraStore()

  def createVhs(dao: SierraDao = createDao, store: SierraStore = createStore): SierraVHS =
    new SierraVHS {
      override protected val versionedDao: SierraDao = dao
      override protected val objectStore: SierraStore = store
    }

  def storeInVHS(transformable: SierraTransformable,
                 vhs: SierraVHS): vhs.VHSEntry =
    vhs
      .update(id = transformable.sierraId.withoutCheckDigit)(
        ifNotExisting = (transformable, EmptyMetadata()))(
        ifExisting = (_, _) =>
          throw new RuntimeException(
            s"Found record ${transformable.sierraId}, but VHS should be empty")
      )
      .right.value

  def storeInVHS(transformables: Seq[SierraTransformable],
                 vhs: SierraVHS): Seq[vhs.VHSEntry] =
    transformables.map { t =>
      storeInVHS(t, vhs = vhs)
    }

  def assertStored(transformable: SierraTransformable,
                   vhs: SierraVHS): Assertion =
    vhs.get(id = transformable.sierraId.withoutCheckDigit).right.value shouldBe transformable

  def assertStoredAndSent(transformable: SierraTransformable, messageSender: MemoryMessageSender, dao: SierraDao, vhs: SierraVHS): Assertion = {
    assertStored(transformable, vhs = vhs)

    val entry = dao.entries(transformable.sierraId.withoutCheckDigit)
    messageSender.getMessages[Entry[String, EmptyMetadata]].contains(entry) shouldBe true
  }
}
