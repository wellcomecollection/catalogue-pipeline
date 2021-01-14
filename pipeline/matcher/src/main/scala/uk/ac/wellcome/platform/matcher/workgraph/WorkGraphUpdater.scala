package uk.ac.wellcome.platform.matcher.workgraph

import grizzled.slf4j.Logging
import org.apache.commons.codec.digest.DigestUtils
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.models.{
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkGraph,
  WorkLinks
}

object WorkGraphUpdater extends Logging {
  def update(links: WorkLinks, existingGraph: WorkGraph): WorkGraph = {
    checkVersionConflicts(links, existingGraph)
    doUpdate(links, existingGraph)
  }

  private def checkVersionConflicts(links: WorkLinks,
                                    existingGraph: WorkGraph) = {
    val maybeExistingNode = existingGraph.nodes.find(_.id == links.workId)
    maybeExistingNode match {
      case Some(WorkNode(_, Some(existingVersion), linkedIds, _)) =>
        if (existingVersion > links.version) {
          val versionConflictMessage =
            s"update failed, work:${links.workId} v${links.version} is not newer than existing work v$existingVersion"
          debug(versionConflictMessage)
          throw VersionExpectedConflictException(versionConflictMessage)
        }
        if (existingVersion == links.version && links.referencedWorkIds != linkedIds.toSet) {
          val versionConflictMessage =
            s"update failed, work:${links.workId} v${links.version} already exists with different content! update-ids:${links.referencedWorkIds} != existing-ids:${linkedIds.toSet}"
          debug(versionConflictMessage)
          throw VersionUnexpectedConflictException(versionConflictMessage)
        }
      case _ => ()
    }
  }

  private def doUpdate(workUpdate: WorkLinks, existingGraph: WorkGraph) = {

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
      existingGraph.nodes.filterNot(_.id == workUpdate.workId)

    // Create a map (work ID) -> (version) for every work in the graph.
    //
    // Every work in the existing graph will be in this list.
    //
    val workVersions: Map[String, Int] =
      linkedWorks.collect {
        case WorkNode(id, Some(version), _, _) => (id, version)
      }.toMap + (workUpdate.workId -> workUpdate.version)

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
      workUpdate.referencedWorkIds.map {
        workUpdate.workId ~> _
      }

    val otherLinks =
      linkedWorks
        .flatMap { node =>
          node.linkedIds.map { node.id ~> _ }
        }

    val links = updateLinks ++ otherLinks

    // Get the IDs of all the works in this graph, and construct a Graph object.
    val workIds =
      existingGraph.nodes
        .flatMap { node =>
          node.id +: node.linkedIds
        } + workUpdate.workId

    val g = Graph.from(edges = links, nodes = workIds)

    // Find all the works that this node links to.
    //
    // e.g.     A → B → C
    //              ↓
    //              D → E
    //
    // In this example, linkedWorkIds(B) = {C, D}
    //
    def linkedWorkIds(n: g.NodeT): List[String] =
      n.diSuccessors.map(_.value).toList.sorted

    // Go through the components of the graph, and turn each of them into
    // a set of WorkNode instances.
    //
    // e.g.     A - B - C       D - E
    //              |           |
    //              F - G       H
    //
    // Here there are two components: (A B C F G) and (D E H)
    //
    WorkGraph(
      g.componentTraverser()
        .flatMap(component => {
          val nodeIds = component.nodes.map(_.value).toList
          component.nodes.map(node => {
            WorkNode(
              id = node.value,
              version = workVersions.get(node.value),
              linkedIds = linkedWorkIds(node),
              componentId = componentIdentifier(nodeIds))
          })
        })
        .toSet
    )
  }

  private def componentIdentifier(nodeIds: List[String]) = {
    DigestUtils.sha256Hex(nodeIds.sorted.mkString("+"))
  }
}
