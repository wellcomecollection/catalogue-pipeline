package uk.ac.wellcome.platform.merger.services

import scala.concurrent.{ExecutionContext, Future}

import grizzled.slf4j.Logging

import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.storage.{Identified, NoVersionExistsError}
import uk.ac.wellcome.storage.store.VersionedStore
import WorkState.Unidentified

/** Before the matcher/merger, the recorder stores a copy of every
  * transformed work in an instance of the VHS.
  *
  * This class looks up recorded works in the VHS, and returns them
  * so the merger has everything it needs to work with.
  *
  */
class RecorderPlaybackService(
  vhs: VersionedStore[String, Int, Work[Unidentified]])(
  implicit ec: ExecutionContext)
    extends Logging {

  /** Given a collection of matched identifiers, return all the
    * corresponding works from VHS.
    */
  def fetchAllWorks(workIdentifiers: Seq[WorkIdentifier])
    : Future[Seq[Option[Work[Unidentified]]]] = {
    Future.sequence(
      workIdentifiers.map(id => Future { getWorkForIdentifier(id) })
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
    workIdentifier: WorkIdentifier): Option[Work[Unidentified]] =
    workIdentifier match {
      case WorkIdentifier(id, Some(version)) =>
        vhs.getLatest(workIdentifier.identifier) match {
          case Right(Identified(_, work)) =>
            if (work.version == version) {
              Some(work)
            } else {
              debug(
                s"VHS version = ${work.version}, identifier version = ${version}, so discarding work")
              None
            }
          case Left(NoVersionExistsError(_)) =>
            throw new NoSuchElementException(s"Work ${id} is not in VHS!")
          case Left(readError) => throw readError.e
        }
      case _ => None
    }
}
