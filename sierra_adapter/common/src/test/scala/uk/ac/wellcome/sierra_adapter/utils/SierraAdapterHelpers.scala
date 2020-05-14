package uk.ac.wellcome.sierra_adapter.utils

import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

trait SierraAdapterHelpers {
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


//  def assertStored(transformable: SierraTransformable,
//                   table: Table): Assertion =
//    assertStored[SierraTransformable](
//      table = table,
//      id = transformable.sierraId.withoutCheckDigit,
//      record = transformable
//    )
//
//  def assertStoredAndSent[T](t: T, id: String, topic: Topic, table: Table)(
//    implicit decoder: Decoder[T]): Assertion = {
//    val hybridRecord = getHybridRecord(table, id = id)
//
//    val storedTransformable = getObjectFromS3[T](
//      bucket = Bucket(hybridRecord.location.namespace),
//      key = hybridRecord.location.key
//    )
//    storedTransformable shouldBe t
//
//    listMessagesReceivedFromSNS(topic).map { info: MessageInfo =>
//      fromJson[HybridRecord](info.message).get
//    } should contain(hybridRecord)
//  }
//
//  def assertStoredAndSent(transformable: SierraTransformable,
//                          topic: Topic,
//                          table: Table): Assertion =
//    assertStoredAndSent[SierraTransformable](
//      transformable,
//      id = transformable.sierraId.withoutCheckDigit,
//      topic = topic,
//      table = table
//    )
}
