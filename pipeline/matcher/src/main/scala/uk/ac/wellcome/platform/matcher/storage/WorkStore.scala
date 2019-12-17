package uk.ac.wellcome.platform.matcher.storage

import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, Version}

import scala.concurrent.Future

class WorkStore(store: VersionedStore[String,
  Int,
  HybridStoreEntry[TransformedBaseWork, EmptyMetadata]]) extends Logging {
  def getWork(key: Version[String, Int]): Future[TransformedBaseWork] =
    store.get(key) match {
      case Left(err) =>
        error(s"Error fetching $key from VHS")
        Future.failed(err.e)
      case Right(Identified(_, entry)) => Future.successful(entry.t)
    }
}
