package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.Retriever

import scala.concurrent.{ExecutionContext, Future}

class SourceWorkLookup(retriever: Retriever[Work[Source]])(implicit ec: ExecutionContext) {
  def fetchAllWorks(workIdentifiers: Seq[WorkIdentifier]): Future[Seq[Option[Work[Source]]]] = {
    assert(
      workIdentifiers.nonEmpty,
      "You should never look up an empty list of WorkIdentifiers!"
    )

    retriever.apply(workIdentifiers.map { _.identifier })
      .map { workMap =>
        workIdentifiers.map { id =>
          val work = workMap(id.identifier)

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
        }
      }
  }
}
