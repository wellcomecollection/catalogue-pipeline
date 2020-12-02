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
      .apply(workIdentifiers.map { _.identifier })
      .map { result =>
        workIdentifiers
          .map { id =>
            getWorkFromResult(result, id)
          }
      }
  }

  private def getWorkFromResult(
    result: RetrieverMultiResult[Work[Source]],
    id: WorkIdentifier
  ): Option[Work[Source]] =
    result.found.get(id.identifier) match {
      case Some(work) =>
        // We only want to get the exact versions of the works specified by
        // the matcher.
        //
        // e.g. if the matcher said "combine Av1 and Bv2", and we look in the retriever
        // and find {Av2, Bv3}, we shouldn't merge these -- we should wait for the matcher
        // to confirm we should still be merging these two works.
        if (id.version.contains(work.version)) {
          Some(work)
        } else {
          None
        }

      case None =>
        result.notFound.get(id.identifier) match {
          case Some(t) if t.isInstanceOf[RetrieverNotFoundException] => None
          case Some(t)                                               => throw t

          // This should be impossible, but handle it so the compiler's happy
          // and we can spot it if it does occur.
          case None =>
            throw new RuntimeException(
              s"Could not find ID $id in retriever result")
        }
    }
}
