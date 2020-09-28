package uk.ac.wellcome.platform.matcher.storage

import grizzled.slf4j.Logging

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.models.VersionExpectedConflictException
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import WorkState.Source

class WorkStore(store: VersionedStore[String, Int, Work[Source]])
    extends Logging {
  def getWork(
    key: Version[String, Int]): Either[Throwable, Work[Source]] =
    store.getLatest(key.id) match {
      case Left(err) =>
        error(s"Error fetching $key from VHS")
        Left(err.e)
      case Right(Identified(id, work)) if id.version == key.version =>
        Right(work)
      case Right(Identified(id, _)) if id.version > key.version =>
        // If the same message gets delivered to the recorder twice in quick succession,
        // the version in the recorder VHS has advanced. This is an expected case
        // where we can simply ignore the current message. To do this, we send a
        // [[VersionExpectedConflictException]] downstream so the message can be deleted.
        Left(MatcherException(VersionExpectedConflictException()))
      case Right(Identified(id, _)) =>
        Left(new Exception(
          s"Version in vhs ${id.version} is lower than requested version ${key.version} for id $id"))
    }
}
