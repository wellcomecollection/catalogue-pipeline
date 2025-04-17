package weco.pipeline.merger.services

import weco.catalogue.internal_model.identifiers.IdentifierType.SierraSystemNumber
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.messaging.MessageSender
import scala.util.Try

class WorkRouter[WorkDestination](
  workSender: MessageSender[WorkDestination],
  pathSender: MessageSender[WorkDestination],
  pathConcatenatorSender: MessageSender[WorkDestination]
) {
  def apply(work: Either[Work[Merged], Work[Denormalised]]): Try[Unit] = {
    work match {
      case Left(work: Work[Merged]) =>
        val sender = work.sourceIdentifier.identifierType match {
          case SierraSystemNumber => pathConcatenatorSender
          case _                  => pathSender
        }
        sender.send(work.data.collectionPath.head.path)

      case Right(work: Work[Denormalised]) =>
        workSender.send(work.id)
    }
  }
}
