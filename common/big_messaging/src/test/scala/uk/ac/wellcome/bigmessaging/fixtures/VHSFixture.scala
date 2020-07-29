package uk.ac.wellcome.bigmessaging.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

trait VHSFixture[T] {
  type VHS = VersionedStore[String, Int, T]

  type InternalStore =
    MemoryStore[Version[String, Int], T] with MemoryMaxima[String, T]

  class MemoryVHS(
    internalStore: InternalStore =
      new MemoryStore[Version[String, Int], T](Map.empty)
      with MemoryMaxima[String, T]
  ) extends MemoryVersionedStore[String, T](internalStore)

  def withVHS[R](testWith: TestWith[VHS, R]): R =
    testWith(new MemoryVHS())
}
