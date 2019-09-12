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
  WorkUpdate
}

import scala.collection.immutable.Iterable

object WorkGraphUpdater extends Logging {
  def update(workUpdate: WorkUpdate, existingGraph: WorkGraph): WorkGraph = {
    checkVersionConflicts(workUpdate, existingGraph)
    doUpdate(workUpdate, existingGraph)
  }

  private def checkVersionConflicts(workUpdate: WorkUpdate, existingGraph: WorkGraph) = {
    val maybeExistingNode = existingGraph.nodes.find(_.id == workUpdate.workId)
    maybeExistingNode match {
      case Some(WorkNode(_, Some(existingVersion), linkedIds, _)) =>
        if (existingVersion > workUpdate.version) {
          val versionConflictMessage =
            s"update failed, work:${workUpdate.workId} v${workUpdate.version} is not newer than existing work v$existingVersion"
          debug(versionConflictMessage)
          throw VersionExpectedConflictException(versionConflictMessage)
        }
        if (existingVersion == workUpdate.version && workUpdate.referencedWorkIds != linkedIds.toSet) {
          val versionConflictMessage =
            s"update failed, work:${workUpdate.workId} v${workUpdate.version} already exists with different content! update-ids:${workUpdate.referencedWorkIds} != existing-ids:${linkedIds.toSet}"
          debug(versionConflictMessage)
          throw VersionUnexpectedConflictException(versionConflictMessage)
        }
      case _ => ()
    }
  }

  private def doUpdate(workUpdate: WorkUpdate, existingGraph: WorkGraph) = {
    val linkedNodes =
      existingGraph.nodes.filterNot(_.id == workUpdate.workId)

    val nodeVersions: Map[String, Int] =
      linkedNodes
        .collect { case WorkNode(id, Some(version), _, _) => (id, version) }
        .toMap + (workUpdate.workId -> workUpdate.version)

    val edges = 
      linkedNodes
        .flatMap { 
          node => toEdges(node.id, node.linkedIds)
        } ++ toEdges(workUpdate.workId, workUpdate.referencedWorkIds)

    val nodeIds =
      existingGraph.nodes
        .flatMap {
          node => node.id +: node.linkedIds
        } + workUpdate.workId

    val g = Graph.from(edges = edges, nodes = nodeIds)

    def adjacentNodeIds(n: g.NodeT) =
      n.diSuccessors.map(_.value).toList.sorted

    WorkGraph(
      g.componentTraverser()
        .flatMap(component => {
          val nodeIds = component.nodes.map(_.value).toList
          component.nodes.map(node => {
            WorkNode(
              node.value,
              nodeVersions.get(node.value),
              adjacentNodeIds(node),
              componentIdentifier(nodeIds))
          })
        })
        .toSet
    )
  }

  private def componentIdentifier(nodeIds: List[String]) = {
    DigestUtils.sha256Hex(nodeIds.sorted.mkString("+"))
  }

  private def toEdges(workId: String, linkedIds: Iterable[String]) =
    linkedIds.map(workId ~> _)
}
