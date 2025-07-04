package weco.pipeline.merger.services

import weco.catalogue.internal_model.identifiers.IdentifierType.SierraSystemNumber
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.lambda.Downstream

import scala.util.Try

class WorkRouter(
  workSender: Downstream,
  pathSender: Downstream,
  pathConcatenatorSender: Downstream
) {
  def apply(work: Work[Merged]): Try[Unit] = {
    work.data.collectionPath match {
      case None => workSender.notify(work.id)
      case Some(CollectionPath(path, _)) =>
        val sender = work.sourceIdentifier.identifierType match {
          case SierraSystemNumber => pathConcatenatorSender
          case _                  => pathSender
        }
        sender.notify(path).map(_ => Nil)
    }
  }
}
