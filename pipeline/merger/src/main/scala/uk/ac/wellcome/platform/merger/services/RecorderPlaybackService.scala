package uk.ac.wellcome.platform.merger.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, VersionedHybridStore}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Before the matcher/merger, the recorder stores a copy of every
  * transformed work in an instance of the VHS.
  *
  * This class looks up recorded works in the VHS, and returns them
  * so the merger has everything it needs to work with.
  *
  */
class RecorderPlaybackService(
  vhs: VersionedHybridStore[String, TransformedBaseWork, EmptyMetadata],
)(implicit ec: ExecutionContext)
    extends Logging {

  /** Given a collection of matched identifiers, return all the
    * corresponding works from VHS.
    */
  def fetchAllWorks(workIdentifiers: Seq[WorkIdentifier])
    : Future[Seq[Option[TransformedBaseWork]]] =
    Future.sequence(
      workIdentifiers
        .map { id => Future.fromTry { getWorkForIdentifier(id) } }
    )

  private def getWorkForIdentifier(
    workIdentifier: WorkIdentifier): Try[Option[TransformedBaseWork]] =
    workIdentifier.version match {
      case 0 => Success(None)
      case _ =>
        vhs.get(id = workIdentifier.identifier) match {
          case Right(work) if work.version == workIdentifier.version =>
            Success(Some(work))
          case Right(work) =>
            debug(
              s"VHS version = ${work.version}, identifier version = ${workIdentifier.version}, so discarding work")
            Success(None)
          case Left(storageError) =>
            Failure(storageError.e)
        }
    }
}
