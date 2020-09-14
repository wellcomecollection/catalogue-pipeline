package uk.ac.wellcome.platform.recorder.services

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import akka.Done

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import WorkState.Unidentified

class RecorderWorkerService[MsgDestination](
  store: VersionedStore[String, Int, Work[Unidentified]],
  messageStream: BigMessageStream[Work[Unidentified]],
  msgSender: MessageSender[MsgDestination])
    extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(work: Work[Unidentified]): Future[Unit] =
    Future.fromTry {
      for {
        key <- storeWork(work)
        _ <- msgSender.sendT[Version[String, Int]](key)
      } yield ()
    }

  private def storeWork(work: Work[Unidentified]): Try[Version[String, Int]] = {
    val result =
      store.upsert(work.sourceIdentifier.toString)(work) {
        case existingWork =>
          Right(
            if (existingWork.version > work.version)
              existingWork
            else
              work
          )
      }
    result match {
      case Right(Identified(key, _)) => Success(key)
      case Left(error)               => Failure(error.e)
    }
  }
}
