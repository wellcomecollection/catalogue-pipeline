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
    checkVersionConflicts(work, affectedNodes)
    doUpdate(work, affectedNodes)
  }

  private def checkVersionConflicts(work: WorkStub,
                                    affectedNodes: Set[WorkNode]): Unit =
    affectedNodes.find(_.id == work.id) match {
      case Some(WorkNode(_, Some(existingVersion), linkedIds, _, _)) =>
        if (existingVersion > work.version) {
          val versionConflictMessage =
            s"update failed, work:${work.id} v${work.version} is not newer than existing work v$existingVersion"
          debug(versionConflictMessage)
          throw VersionExpectedConflictException(versionConflictMessage)
        }
        if (existingVersion == work.version && work.referencedWorkIds != linkedIds.toSet) {
          val versionConflictMessage =
            s"update failed, work:${work.id} v${work.version} already exists with different content! update-ids:${work.referencedWorkIds} != existing-ids:${linkedIds.toSet}"
          debug(versionConflictMessage)
          throw VersionUnexpectedConflictException(versionConflictMessage)
        }
      case _ => ()
    }

  private def doUpdate(work: WorkStub,
                       affectedNodes: Set[WorkNode]): Set[WorkNode] = {

    // Find everything that's in the existing graph, but which isn't
    // the node we're updating.
    //
    // e.g.     A   B
    //           \ /
    //            C
    //           / \
    //          D   E
    //
    // If we're updating work B, then this list will be (A C D E).
    //
    val linkedWorks =
      affectedNodes.filterNot(_.id == work.id)

    // Create a map (work ID) -> (version) for every work in the graph.
    //
    // Every work in the existing graph will be in this list.
    //
    val workVersions: Map[CanonicalId, Int] =
      linkedWorks.collect {
        case WorkNode(id, Some(version), _, _, _) => (id, version)
      }.toMap + (work.id -> work.version)

    // Create a set of all Works that are suppressed at the source.  We shouldn't
    // include any of these in the final graph.
    val suppressedWorks: Set[CanonicalId] =
      (linkedWorks.map(w => (w.id, w.suppressed)) ++ Set(
        (work.id, work.suppressed)))
        .collect { case (workId, true) => workId }

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
    val updateLinks =
      work.referencedWorkIds.map {
        work.id ~> _
      }

    val otherLinks =
      linkedWorks
        .flatMap { node =>
          node.linkedIds.map { node.id ~> _ }
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
    val allLinks = updateLinks ++ otherLinks
    val unsuppressedLinks = allLinks
      .filterNot { lk =>
        suppressedWorks.contains(lk.head) || suppressedWorks.contains(lk.to)
      }

    // Get the IDs of all the works in this graph, and construct a Graph object.
    val workIds =
      affectedNodes
        .flatMap { node =>
          node.id +: node.linkedIds
        } + work.id

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
    def linkedWorkIds(n: g.NodeT): List[CanonicalId] =
      allLinks
        .collect { case link if link.head == n.value => link.to }
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
          WorkNode(
            id = node.value,
            version = workVersions.get(node.value),
            linkedIds = linkedWorkIds(node),
            componentId = ComponentId(nodeIds),
            suppressed = suppressedWorks.contains(node.value)
          )
        })
      })
      .toSet
  }
}
