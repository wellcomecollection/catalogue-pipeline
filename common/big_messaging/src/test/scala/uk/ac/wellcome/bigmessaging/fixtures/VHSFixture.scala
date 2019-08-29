package uk.ac.wellcome.bigmessaging.fixtures

import scala.util.{Success, Try}

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.bigmessaging.{EmptyMetadata, GetLocation}

import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.{
  ObjectLocation,
  StoreWriteError,
  Version
}
import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.messaging.memory.MemoryMessageSender

trait VHSFixture[T] extends BigMessagingFixture {

  type Entry = HybridStoreEntry[T, EmptyMetadata]

  type VHS = VersionedStore[String, Int, Entry] with GetLocation

  type InternalStore =
    MemoryStore[Version[String, Int], Entry] with MemoryMaxima[String, Entry]

  class MemoryVHS(
    internalStore: InternalStore =
      new MemoryStore[Version[String, Int], Entry](Map.empty)
      with MemoryMaxima[String, Entry]
  ) extends MemoryVersionedStore[String, Entry](internalStore)
      with GetLocation {

    def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
      Success(ObjectLocation("test", s"${key.id}/${key.version}"))
  }

  class BrokenMemoryVHS extends MemoryVHS() {
    override def put(id: Version[String, Int])(entry: Entry): WriteEither =
      Left(StoreWriteError(new Error("BOOM!")))
  }

  def withVHS[R](testWith: TestWith[VHS, R]): R =
    testWith(new MemoryVHS())

  def withBrokenVHS[R](testWith: TestWith[VHS, R]): R =
    testWith(new BrokenMemoryVHS())

  def withMemoryMessageSender[R](
    testWith: TestWith[MemoryMessageSender, R]): R = {
    testWith(new MemoryMessageSender())
  }
}
