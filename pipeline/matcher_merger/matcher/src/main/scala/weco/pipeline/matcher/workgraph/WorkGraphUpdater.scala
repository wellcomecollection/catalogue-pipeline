package weco.pipeline.matcher.workgraph

import grizzled.slf4j.Logging
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.models.{
  SourceWorkData,
  SubgraphId,
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkNode,
  WorkStub
}

object WorkGraphUpdater extends Logging {
  def update(work: WorkStub, affectedNodes: Set[WorkNode]): Set[WorkNode] = {
    val affectedWorks = affectedNodes.map {
      n =>
        n.id -> n
    }.toMap

    checkVersionConflicts(work, affectedWorks)

    val newSubgraph = new WorkSubgraph(
      newWork = WorkNode(
        id = work.id,
        subgraphId = SubgraphId(work.id),
        componentIds = List(work.id),
        sourceWork = Some(
          SourceWorkData(
            id = work.state.sourceIdentifier,
            version = work.version,
            suppressed = work.suppressed,
            mergeCandidateIds = work.mergeCandidateIds.toList.sorted
          )
        )
      ),
      existingWorks = affectedWorks.filterNot { case (id, _) => id == work.id }
    )

    newSubgraph.create
  }

  private def checkVersionConflicts(
    work: WorkStub,
    affectedWorks: Map[CanonicalId, WorkNode]
  ): Unit =
    affectedWorks.get(work.id).flatMap(_.sourceWork) match {
      case Some(SourceWorkData(_, existingVersion, _, _))
          if existingVersion > work.version =>
        val versionConflictMessage =
          s"update failed, work:${work.id} v${work.version} is not newer than existing work v$existingVersion"
        debug(versionConflictMessage)
        throw VersionExpectedConflictException(versionConflictMessage)

      case Some(
            SourceWorkData(_, existingVersion, _, existingMergeCandidateIds)
          )
          if existingVersion == work.version && work.mergeCandidateIds != existingMergeCandidateIds.toSet =>
        val versionConflictMessage =
          s"update failed, work:${work.id} v${work.version} already exists with different content! update-ids:${work.mergeCandidateIds} != existing-ids:${existingMergeCandidateIds.toSet}"
        debug(versionConflictMessage)
        throw VersionUnexpectedConflictException(versionConflictMessage)

      case _ => ()
    }
}

private class WorkSubgraph(
  newWork: WorkNode,
  existingWorks: Map[CanonicalId, WorkNode]
) {
  require(!existingWorks.contains(newWork.id))

  // This is a lookup of all the works in this update
  val allWorks: Map[CanonicalId, WorkNode] =
    existingWorks + (newWork.id -> newWork)

  lazy val sourceWorks: Map[CanonicalId, SourceWorkData] =
    allWorks
      .collect {
        case (id, WorkNode(_, _, _, Some(sourceWork))) =>
          id -> sourceWork
      }

  def create: Set[WorkNode] = {
    // Create a list of all the connections between works in the graph.
    //
    // e.g.     A → B → C
    //              ↓
    //              D → E
    //
    // If we're updating work D, then the lists will be:
    //
    //    updateLinks = (D → E)
    //    otherLinks  = (A → B, B → C, B → D)
    //
    val links =
      sourceWorks
        .flatMap {
          case (id, sourceWork) =>
            sourceWork.mergeCandidateIds.map { id ~> _ }
        }

    // Remove any links that come to/from works that are suppressed.
    // This means we won't match "through" these works.
    //
    // e.g.     A → B → C → (S) → D → E
    //
    // If work S was suppressed, then the graph would be split into three disconnected
    // components:
    //
    //          A → B → C
    //          S
    //          D → E
    //
    // We record information about suppressions in the matcher database.
    val unsuppressedLinks = links
      .filterNot {
        link =>
          link.head.isSuppressed || link.to.isSuppressed
      }

    // Get the IDs of all the works in this graph, and construct a Graph object.
    val workIds =
      sourceWorks.flatMap {
        case (id, work) =>
          id +: work.mergeCandidateIds
      }.toSet

    val g = Graph.from(edges = unsuppressedLinks, nodes = workIds)

    // Go through the components of the graph, and turn each of them into
    // a set of WorkNode instances.
    //
    // e.g.     A - B - C       D - E
    //              |           |
    //              F - G       H
    //
    // Here there are two components: (A B C F G) and (D E H)
    //
    // This subgraphId contains all the works in an update, regardless of
    // whether they're in the same component. Even when graphs are split into
    // multiple components, they all share the same subgraph ID. This is to
    // allow graphs to be reconnected by preserving edge relationships that
    // span components.
    //
    // e.g.
    //        A->B->C->D->E
    //
    //    If we update work C, the graph will be split into two components:
    //
    //        A->B->C
    //        D->E
    //
    // The subgraphId for both components will be the same, so that when we
    // recombine the graph, we can still match across the C->D edge.

    val subgraphId = SubgraphId(workIds)

    g.componentTraverser()
      .flatMap(
        component => {
          val componentIds = component.nodes.map(_.value).toList.sorted

          component.nodes.map(
            node => {
              val id = node.value

              WorkNode(
                id = id,
                subgraphId = subgraphId,
                componentIds = componentIds,
                sourceWork = sourceWorks.get(id)
              )
            }
          )
        }
      )
      .toSet
  }

  implicit class WorkOps(id: CanonicalId) {
    def isSuppressed: Boolean =
      sourceWorks.get(id).exists(_.suppressed)
  }
}
