package weco.pipeline.merger.services

import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.pipeline.matcher.models.WorkStub
import weco.pipeline_storage.{Retriever, RetrieverMultiResult}

import scala.concurrent.{ExecutionContext, Future}

class IdentifiedWorkLookup(retriever: Retriever[Work[Identified]])(
  implicit ec: ExecutionContext) {
  def fetchAllWorks(works: Seq[WorkStub])
    : Future[Seq[Option[Work[Identified]]]] = {
    assert(
      works.nonEmpty,
      "You should never look up an empty list of WorkIdentifiers!"
    )

    val workIds = works.map(_.id.toString)

    retriever(workIds)
      .map {
        case RetrieverMultiResult(retrievedWorks, notFound) if notFound.isEmpty =>
          works
            .map { case WorkStub(id, modifiedTime, _) =>
              val work = retrievedWorks(id.toString)
                // We only want to get the exact versions of the works specified
                // by the matcher.
                //
                // e.g. if the matcher said "combine Av1 and Bv2", and we look
                // in the retriever and find {Av2, Bv3}, we shouldn't merge
                // these -- we should wait for the matcher to confirm we should
                // still be merging these two works.
                if (work.state.modifiedTime == modifiedTime) Some(work) else None
            }
        case RetrieverMultiResult(_, notFound) =>
          throw new RuntimeException(s"Works not found: $notFound")
      }
  }
}
