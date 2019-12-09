package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import com.amazonaws.services.s3.AmazonS3
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.mets_adapter.models.MetsData
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.mets.client.AssumeRoleClientProvider
import uk.ac.wellcome.platform.transformer.mets.parsers.MetsXmlParser
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class MetsTransformerWorkerService(
                                    msgStream: SQSStream[NotificationMessage],
                                    messageSender: BigMessageSender[SNSConfig, TransformedBaseWork],
                                    adapterStore: VersionedStore[String, Int, MetsData],
                                  mestStoreAssumeRoleClient: AssumeRoleClientProvider[AmazonS3]
                                  )
    extends Runnable
    with Logging {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.foreach(this.getClass.getSimpleName, processAndLog)

  def processAndLog(message: NotificationMessage): Future[Unit] = {
    val tried = for {
      key <- fromJson[Version[String, Int]](message.body)
      _ <- process(key).toTry
    } yield ()
    Future.fromTry(tried.recover {
      case t =>
        error(s"There was an error processing $message: ", t)
        throw t
    })
  }

  private def process(key: Version[String, Int]) = {
    for {
      metsData <- getKey(key)
      metsString <- fetchMets(metsData)
      mets <- MetsXmlParser(metsString)
      work <- mets.toWork(key.version)
      _ <- messageSender.sendT(work).toEither
    } yield ()
  }

  private def getKey(key: Version[String, Int]) = {
    adapterStore.get(key) match {
      case Left(err)                   => Left(err.e)
      case Right(Identified(_, entry)) => Right(entry)
    }
  }

  private def fetchMets(metsData: MetsData) = {
    S3TypedStore[String](implicitly[Codec[String]], mestStoreAssumeRoleClient.getClient)
      .get(ObjectLocation(metsData.bucket, metsData.path+metsData.file))
      .right.map(_.identifiedT.t)
      .left.map(error => error.e)
  }
}

case class VersionedMets(version: Int, metsString: String)
