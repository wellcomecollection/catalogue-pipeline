package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.matcher.WorkIdentifier
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{Retriever, RetrieverMultiResult}

import scala.concurrent.{ExecutionContext, Future}

class IdentifiedWorkLookup(retriever: Retriever[Work[Identified]])(
  implicit ec: ExecutionContext) {
  def fetchAllWorks(workIdentifiers: Seq[WorkIdentifier])
    : Future[Seq[Option[Work[Identified]]]] = {
    assert(
      workIdentifiers.nonEmpty,
      "You should never look up an empty list of WorkIdentifiers!"
    )

    val workIds = workIdentifiers
      .collect {
        case WorkIdentifier(identifier, Some(_)) => identifier
      }

    // A WorkIdentifier can have a version "None" if a Work in the pipeline points to
    // it, but we haven't seen a Work with this identifier yet.
    //
    // e.g. Suppose a Work with identifier A says "I am matched to identifier B", but
    // the pipeline hasn't seen a Work with identifier B yet.  Then the merger would
    // receive something like:
    //
    //      WorkIdentifier("A", version = Some(1)), WorkIdentifier("B", version = None)
    //
    // Every message from the matcher should be triggered by a Work in the pipeline,
    // so every set of identifiers it sends should include at least one WorkIdentifier
    // with a non-empty version.
    //
    // If it doesn't, something has gone wrong and we should give up immediately.
    assert(
      workIds.nonEmpty,
      s"At least one of the WorkIdentifiers should have a version ($workIdentifiers)"
    )

    retriever(workIds)
      .map {
        case RetrieverMultiResult(works, notFound) if notFound.isEmpty =>
          workIdentifiers
            .map {
              case WorkIdentifier(_, None) => None
              case WorkIdentifier(id, Some(version)) =>
                val work = works(id)
                // We only want to get the exact versions of the works specified
                // by the matcher.
                //
                // e.g. if the matcher said "combine Av1 and Bv2", and we look
                // in the retriever and find {Av2, Bv3}, we shouldn't merge
                // these -- we should wait for the matcher to confirm we should
                // still be merging these two works.
                if (work.version == version) Some(work) else None
            }
        case RetrieverMultiResult(_, notFound) =>
          throw new RuntimeException(s"Works not found: $notFound")
      }
  }
}
