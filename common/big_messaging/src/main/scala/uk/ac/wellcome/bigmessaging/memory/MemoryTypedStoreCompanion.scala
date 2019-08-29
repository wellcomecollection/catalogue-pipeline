package uk.ac.wellcome.bigmessaging.memory

import uk.ac.wellcome.storage.store.memory.{MemoryStreamStore, MemoryTypedStore}
import uk.ac.wellcome.storage.streaming.Codec

// TODO: Should probably live as a companion object in the storage lib
object MemoryTypedStoreCompanion {
  def apply[Ident, T]()(
    implicit codecT: Codec[T]): MemoryTypedStore[Ident, T] = {
    implicit val memoryStreamStore: MemoryStreamStore[Ident] =
      MemoryStreamStore[Ident]()

    new MemoryTypedStore[Ident, T](Map.empty)
  }
}
