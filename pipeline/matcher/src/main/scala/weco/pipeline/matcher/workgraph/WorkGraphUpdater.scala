package weco.pipeline.matcher.workgraph

import grizzled.slf4j.Logging
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.models.{
  ComponentId,
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkNode,
  WorkStub
}

object WorkGraphUpdater extends Logging {
  def update(work: WorkStub, affectedNodes: Set[WorkNode]): Set[WorkNode] = {
    val affectedWorks = affectedNodes.map { n => n.id -> n }.toMap

    checkVersionConflicts(work, affectedWorks)

    val newSubgraph = new WorkSubgraph(
      newWork = WorkNode(
        id = work.id,
        version = work.version,
        linkedIds = work.referencedWorkIds.toList,
        componentId = ComponentId(work.id),
        suppressed = work.suppressed
      ),
      existingWorks = affectedWorks.filterNot { case (id, _) => id == work.id }
    )

    newSubgraph.create
  }

  private def checkVersionConflicts(work: WorkStub,
                                    affectedWorks: Map[CanonicalId, WorkNode]): Unit =
    affectedWorks.get(work.id) match {
      case Some(WorkNode(_, Some(existingVersion), _, _, _)) if existingVersion > work.version =>
        val versionConflictMessage =
          s"update failed, work:${work.id} v${work.version} is not newer than existing work v$existingVersion"
        debug(versionConflictMessage)
        throw VersionExpectedConflictException(versionConflictMessage)

      case Some(WorkNode(_, Some(existingVersion), linkedIds, _, _))
        if existingVersion == work.version && work.referencedWorkIds != linkedIds.toSet =>
          val versionConflictMessage =
            s"update failed, work:${work.id} v${work.version} already exists with different content! update-ids:${work.referencedWorkIds} != existing-ids:${linkedIds.toSet}"
          debug(versionConflictMessage)
          throw VersionUnexpectedConflictException(versionConflictMessage)

      case _ => ()
    }
}

private class WorkSubgraph(newWork: WorkNode, existingWorks: Map[CanonicalId, WorkNode]) {
  require(!existingWorks.contains(newWork.id))

  // This is a lookup of all the works in this update
  val allWorks: Map[CanonicalId, WorkNode] = existingWorks + (newWork.id -> newWork)

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
      allWorks.flatMap { case (id, work) =>
        work.linkedIds.map { id ~> _ }
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
      .filterNot { link => link.head.isSuppressed || link.to.isSuppressed }

    // Get the IDs of all the works in this graph, and construct a Graph object.
    val workIds =
      allWorks.flatMap { case (id, work) => id +: work.linkedIds }

    val g = Graph.from(edges = unsuppressedLinks, nodes = workIds)

    // Find all the works that this node links to.
    //
    // e.g.     A → B → C
    //              ↓
    //              D → E
    //
    // In this example, linkedWorkIds(B) = {C, D}
    //
    // Note: this includes all the links starting from this work, even links that aren't
    // being used for the matcher result.  e.g. linkedWorkIds(B) would be the same even
    // if work D was suppressed.
    //
    def linkedWorkIds(id: CanonicalId): List[CanonicalId] =
      links
        .collect { case link if link.head == id => link.to }
        .toList
        .sorted

    // Go through the components of the graph, and turn each of them into
    // a set of WorkNode instances.
    //
    // e.g.     A - B - C       D - E
    //              |           |
    //              F - G       H
    //
    // Here there are two components: (A B C F G) and (D E H)
    //
    g.componentTraverser()
      .flatMap(component => {
        val nodeIds = component.nodes.map(_.value).toList
        component.nodes.map(node => {
          val id = node.value

          WorkNode(
            id = id,
            version = allWorks.get(id).flatMap(_.version),
            linkedIds = linkedWorkIds(id),
            componentId = ComponentId(nodeIds),
            suppressed = id.isSuppressed
          )
        })
      })
      .toSet
  }

  implicit class WorkOps(id: CanonicalId) {
    def isSuppressed: Boolean =
      allWorks.get(id).exists(_.suppressed)
  }
}
