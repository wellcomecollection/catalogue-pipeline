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

    // If none of the work identifiers had a version, we'll discard anything
    // we get from the retriever, so skip going out to it.
    if (workIds.isEmpty) {
      Future.successful(
        workIdentifiers.map { _ => None }
      )
    } else {
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
}
