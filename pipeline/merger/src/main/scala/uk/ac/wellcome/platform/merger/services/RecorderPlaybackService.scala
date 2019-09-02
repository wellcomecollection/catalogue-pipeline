package uk.ac.wellcome.platform.merger.services

import scala.util.{Try, Success, Failure}
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.internal.TransformedBaseWork

import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.storage.{Version, Identified}
import uk.ac.wellcome.storage.store.{VersionedStore, HybridStoreEntry}

import scala.concurrent.{ExecutionContext, Future}

/** Before the matcher/merger, the recorder stores a copy of every
  * transformed work in an instance of the VHS.
  *
  * This class looks up recorded works in the VHS, and returns them
  * so the merger has everything it needs to work with.
  *
  */
class RecorderPlaybackService(
  vhs: VersionedStore[String,
                      Int,
                      HybridStoreEntry[TransformedBaseWork, EmptyMetadata]])(
  implicit ec: ExecutionContext) extends Logging {

  /** Given a collection of matched identifiers, return all the
    * corresponding works from VHS.
    */
  def fetchAllWorks(workIdentifiers: Seq[WorkIdentifier])
    : Future[Seq[Option[TransformedBaseWork]]] = {
    Future.sequence(
      workIdentifiers.map(id => Future.fromTry(getWorkForIdentifier(id)))
    )
  }

  /** Retrieve a single work from the recorder table.
    *
    * If the work is present in VHS but has a different version to what
    * we're expecting, this method returns [[None]].
    *
    * If the work is missing from VHS, it throws [[NoSuchElementException]].
    */
  private def getWorkForIdentifier(
    workIdentifier: WorkIdentifier): Try[Option[TransformedBaseWork]] =
    vhs.getLatest(workIdentifier.identifier) match {
      case Left(readError) => Failure(new Error(s"${readError}"))
      case Right(Identified(Version(_, version), HybridStoreEntry(work, _))) => 
        if (work.version == workIdentifier.version) { Success(Some(work)) }
        else {
          debug(
            s"VHS version = ${work.version}, identifier version = ${workIdentifier.version}, so discarding work")
          Success(None)
        }
    }
}
