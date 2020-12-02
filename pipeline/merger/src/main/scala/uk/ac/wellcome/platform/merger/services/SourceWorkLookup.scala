package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{
  Retriever,
  RetrieverMultiResult,
  RetrieverNotFoundException
}

import scala.concurrent.{ExecutionContext, Future}

class SourceWorkLookup(retriever: Retriever[Work[Source]])(
  implicit ec: ExecutionContext) {
  def fetchAllWorks(
    workIdentifiers: Seq[WorkIdentifier]): Future[Seq[Option[Work[Source]]]] = {
    assert(
      workIdentifiers.nonEmpty,
      "You should never look up an empty list of WorkIdentifiers!"
    )

    retriever
      .apply(
        // We only want the exact versions of works from the matcher; if an identifier
        // doesn't have a version, don't both trying to retrieve it.
        workIdentifiers
          .filter{ _.version.isDefined }
          .map { _.identifier }
      )
      .map { result =>
        workIdentifiers
          .map { id =>
            id -> getWorkFromResult(result, id)
          }
          .map {
            // We only want to get the exact versions of the works specified by
            // the matcher.
            //
            // e.g. if the matcher said "combine Av1 and Bv2", and we look in the retriever
            // and find {Av2, Bv3}, we shouldn't merge these -- we should wait for the matcher
            // to confirm we should still be merging these two works.
            case (id, Some(work)) if id.version.contains(work.version) => Some(work)
            case _ => None
          }
      }
  }

  private def getWorkFromResult(
    result: RetrieverMultiResult[Work[Source]],
    id: WorkIdentifier
  ): Option[Work[Source]] =
    result.found.get(id.identifier) match {
      case Some(work) => Some(work)

      case None =>
        result.notFound.get(id.identifier) match {
          case Some(t) if t.isInstanceOf[RetrieverNotFoundException] => None
          case Some(t) => throw t

          // We didn't look up this identifier, because it doesn't have a version.
          case None =>
            assert(id.version.isEmpty, s"Retriever failed to find an identifier: $id")
            None
        }
    }
}
