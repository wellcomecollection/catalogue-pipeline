package uk.ac.wellcome.platform.matcher.storage

import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, Version}


class WorkStore(store: VersionedStore[String,
  Int,
  HybridStoreEntry[TransformedBaseWork, EmptyMetadata]]) extends Logging {
  def getWork(key: Version[String, Int]): Either[Throwable, Option[TransformedBaseWork]] =
    store.getLatest(key.id) match {
      case Left(err) =>
        error(s"Error fetching $key from VHS")
        Left(err.e)
      case Right(Identified(id, entry)) if id.version == key.version => Right(Some(entry.t))
      case Right(Identified(id, _)) if id.version > key.version => Right(None)
      case Right(Identified(id, _)) => Left(new Exception(s"Version in vhs ${id.version} is lower than requested version ${key.version} for id $id"))
    }
}
