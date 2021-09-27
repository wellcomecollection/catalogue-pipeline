package weco.pipeline.matcher.workgraph

import grizzled.slf4j.Logging
import org.apache.commons.codec.digest.DigestUtils
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.models.{
  VersionExpectedConflictException,
  VersionUnexpectedConflictException,
  WorkNode,
  WorkStub
}

object WorkGraphUpdater extends Logging {
  def update(work: WorkStub, existingNodes: Set[WorkNode]): Set[WorkNode] = {
    checkVersionConflicts(work, existingNodes)
    doUpdate(work, existingNodes)
  }

  private def checkVersionConflicts(work: WorkStub,
                                    existingNodes: Set[WorkNode]): Unit = {
    val maybeExistingNode = existingNodes.find(_.id == work.id)
    maybeExistingNode match {
      case Some(WorkNode(_, Some(existingVersion), linkedIds, _)) =>
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
  }

  private def doUpdate(work: WorkStub,
                       existingNodes: Set[WorkNode]): Set[WorkNode] = {

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
      existingNodes.filterNot(_.id == work.id)

    // Create a map (work ID) -> (version) for every work in the graph.
    //
    // Every work in the existing graph will be in this list.
    //
    val workVersions: Map[CanonicalId, Int] =
      linkedWorks.collect {
        case WorkNode(id, Some(version), _, _) => (id, version)
      }.toMap + (work.id -> work.version)

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

    val links = updateLinks ++ otherLinks

    // Get the IDs of all the works in this graph, and construct a Graph object.
    val workIds =
      existingNodes
        .flatMap { node =>
          node.id +: node.linkedIds
        } + work.id

    val g = Graph.from(edges = links, nodes = workIds)

    // Find all the works that this node links to.
    //
    // e.g.     A → B → C
    //              ↓
    //              D → E
    //
    // In this example, linkedWorkIds(B) = {C, D}
    //
    def linkedWorkIds(n: g.NodeT): List[CanonicalId] =
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
  }

  /** Create the "component identifier".
    *
    * This is shared by all the Works in the same component -- i.e., all the
    * Works that are matched together.
    *
    * Note that this is based on the *unversioned* identifiers.  This means the
    * component identifier is stable across different versions of a Work.
    *
    * TODO: Does this need to be a SHA-256 value?
    * Could we just concatenate all the IDs?
    */
  private def componentIdentifier(nodeIds: List[CanonicalId]): String =
    DigestUtils.sha256Hex(nodeIds.sorted.map(_.underlying).mkString("+"))
}
