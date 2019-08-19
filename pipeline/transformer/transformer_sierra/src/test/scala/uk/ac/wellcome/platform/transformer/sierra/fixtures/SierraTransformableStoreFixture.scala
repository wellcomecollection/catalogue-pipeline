package uk.ac.wellcome.platform.transformer.sierra.fixtures

import uk.ac.wellcome.storage.store.{HybridStore, HybridIndexedStoreEntry}
import uk.ac.wellcome.storage.{ObjectLocation, Version}
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryTypedStore, MemoryStreamStore}
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.platform.transformer.sierra.services.EmptyMetadata

trait SierraTransformableStoreFixture {

  type Ident = Version[String, Int]

  type IndexEntry = HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]

  type SierraTransformableStore = HybridStore[
    Ident,
    ObjectLocation,
    SierraTransformable,
    EmptyMetadata
  ]

  implicit val memoryStreamStore: MemoryStreamStore[ObjectLocation] =
    MemoryStreamStore[ObjectLocation]()

  implicit val sierraTransformableStore : SierraTransformableStore =
    new SierraTransformableStore {
      override implicit val indexedStore = new MemoryStore[Ident, IndexEntry](Map.empty)

      override implicit val typedStore: MemoryTypedStore[ObjectLocation, SierraTransformable] =
        new MemoryTypedStore[ObjectLocation, SierraTransformable](Map.empty)

      override def createTypeStoreId(id: Ident): ObjectLocation =
        ObjectLocation(id.version.toString, id.id)
    }
}
