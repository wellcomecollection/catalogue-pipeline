package uk.ac.wellcome.sierra_adapter.utils

import io.circe.Decoder
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

trait SierraAdapterHelpers extends Matchers {
  type SierraVHS = VersionedStore[String, Int, SierraTransformable]

  def withSierraVHS[R](
    testWith: TestWith[SierraVHS, R]): R =
    testWith(new MemoryVersionedStore(new MemoryStore(Map[Version[String, Int],SierraTransformable]()) with MemoryMaxima[String, SierraTransformable]))


  def storeInVHS(transformable: SierraTransformable,
                 hybridStore: SierraVHS): hybridStore.WriteEither =
    hybridStore.putLatest(transformable.sierraId.withoutCheckDigit)(transformable)

  def storeInVHS(transformables: List[SierraTransformable],
                 hybridStore: SierraVHS): Seq[hybridStore.WriteEither] =

      transformables.map { t =>
        storeInVHS(t, hybridStore = hybridStore)
      }


  def assertStored[T](id: String, t: T, store: VersionedStore[String, Int, T]): Assertion =
    store.getLatest(id).right.get.identifiedT shouldBe t


  def assertStoredAndSent[T](id: String,t: T, store: VersionedStore[String, Int, T], messageSender: MemoryBigMessageSender[T])(
    implicit decoder: Decoder[T]): Assertion = {
    assertStored(id,t, store)
    messageSender.getMessages[T] should contain(t)
  }
}
