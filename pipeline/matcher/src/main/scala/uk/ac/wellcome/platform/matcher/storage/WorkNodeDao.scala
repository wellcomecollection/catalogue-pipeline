package uk.ac.wellcome.platform.matcher.storage

import uk.ac.wellcome.models.matcher.WorkNode

import scala.util.{Failure, Success, Try}

trait WorkNodeDao {
  def put(work: WorkNode): Try[WorkNode]

  def get(ids: Set[String]): Try[Set[WorkNode]]

  def getByComponentIds(setIds: Set[String]): Try[Set[WorkNode]] = {
    val results: Set[Try[Seq[WorkNode]]] = setIds.map { getByComponentId }

    val successes = results.collect { case Success(s) => s }
    val failures = results.collect { case Failure(err) => err }

    if (failures.isEmpty) {
      Success(successes.flatten)
    } else {
      Failure(new Throwable(s"Error looking up set IDs $setIds: $failures"))
    }
  }

  def getByComponentId(componentId: String): Try[Seq[WorkNode]]
}
