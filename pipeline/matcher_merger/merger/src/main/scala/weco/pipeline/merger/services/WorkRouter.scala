package weco.pipeline.merger.services

import weco.catalogue.internal_model.identifiers.IdentifierType.SierraSystemNumber
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.messaging.MessageSender

import scala.util.Try

class WorkRouter[WorkDestination](
  workSender: MessageSender[WorkDestination],
  pathSender: MessageSender[WorkDestination],
  pathConcatenatorSender: MessageSender[WorkDestination]
) {
  def apply(work: Work[Merged]): Try[Unit] = {
    work.data.collectionPath match {
      case None => workSender.send(work.id)
      case Some(CollectionPath(path, _)) =>
        val sender = work.sourceIdentifier.identifierType match {
          case SierraSystemNumber => pathConcatenatorSender
          case _                  => pathSender
        }
        sender.send(path).map(_ => Nil)
    }
  }
}
