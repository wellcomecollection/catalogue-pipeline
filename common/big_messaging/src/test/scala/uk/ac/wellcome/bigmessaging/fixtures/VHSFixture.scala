package uk.ac.wellcome.bigmessaging.fixtures

import uk.ac.wellcome.fixtures.TestWith

import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.{StoreReadError, StoreWriteError, Version}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima

trait VHSFixture[T] {
  type VHS = VersionedStore[String, Int, T]

  type InternalStore =
    MemoryStore[Version[String, Int], T] with MemoryMaxima[String, T]

  class MemoryVHS(
    internalStore: InternalStore =
      new MemoryStore[Version[String, Int], T](Map.empty)
      with MemoryMaxima[String, T]
  ) extends MemoryVersionedStore[String, T](internalStore)

  class BrokenMemoryVHS extends MemoryVHS() {
    override def put(id: Version[String, Int])(item: T): WriteEither =
      Left(StoreWriteError(new Error("BOOM!")))
    override def get(id: Version[String, Int]): ReadEither =
      Left(StoreReadError(new Error("BOOM!")))
  }

  def withVHS[R](testWith: TestWith[VHS, R]): R =
    testWith(new MemoryVHS())

  def withBrokenVHS[R](testWith: TestWith[VHS, R]): R =
    testWith(new BrokenMemoryVHS())
}
