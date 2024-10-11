package weco.pipeline.merger.services

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work
import weco.pipeline.matcher.models.WorkIdentifier
import weco.pipeline_storage.{Retriever, RetrieverMultiResult}

import scala.concurrent.{ExecutionContext, Future}

class IdentifiedWorkLookup(
  retriever: Retriever[Work[Identified]],
  checkLatestVersion: Boolean = true
)(
  implicit ec: ExecutionContext
) extends Logging {
  def fetchAllWorks(
    workIdentifiers: Seq[WorkIdentifier]
  ): Future[Seq[Option[Work[Identified]]]] = {
    assert(
      workIdentifiers.nonEmpty,
      "You should never look up an empty list of WorkIdentifiers!"
    )

    val workIds = workIdentifiers.map { _.identifier.toString }
    assert(workIds.nonEmpty)

    retriever(workIds)
      .map {
        case RetrieverMultiResult(works, notFound) if notFound.isEmpty =>
          workIdentifiers
            .map {
              case WorkIdentifier(id, version) =>
                val work = works(id.toString)
                // Being asked to merge non-matching versions is incorrect
                // but it is possible for the version in the identified index
                // to be higher than the version in the graph store.
                // Choosing between:
                // (a) a complete failure where no works are merged
                // (b) a partial failure where only the works with matching versions are merged
                // (c) a partial success where all works are merged, accepting the risk of inconsistency
                // We choose (c) as the least disruptive option.
                if (work.version != version) {
                  warn(
                    "Matching version inconsistent! " +
                      s"Found work $work with version $version, expected ${work.version}"
                  )
                }
                Some(work)
            }
        case RetrieverMultiResult(_, notFound) =>
          throw new RuntimeException(s"Works not found: $notFound")
      }
  }
}
