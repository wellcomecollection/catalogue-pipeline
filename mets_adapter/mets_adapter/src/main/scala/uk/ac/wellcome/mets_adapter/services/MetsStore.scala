package uk.ac.wellcome.mets_adapter.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.storage.VersionAlreadyExistsError
import uk.ac.wellcome.storage.store.{VersionedStore, HybridStoreEntry}
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.bigmessaging.EmptyMetadata

class MetsStore(store: VersionedStore[String, Int, HybridStoreEntry[String, EmptyMetadata]]) extends Logging {

  def storeXml(key: Version[String, Int],
               xml: String): Either[Throwable, Version[String, Int]] =
    store
      .put(key)(HybridStoreEntry(xml, EmptyMetadata()))
      .right
      .map { case Identified(key, _) => key }
      .left
      .flatMap {
        case VersionAlreadyExistsError(_) =>
          warn(s"$key already exists in VHS so re-publishing")
          Right(key)
        case err => Left(err.e)
      }
}

object MetsStore {
  
  def apply(store: VersionedStore[String, Int, HybridStoreEntry[String, EmptyMetadata]]) =
    new MetsStore(store)
}
