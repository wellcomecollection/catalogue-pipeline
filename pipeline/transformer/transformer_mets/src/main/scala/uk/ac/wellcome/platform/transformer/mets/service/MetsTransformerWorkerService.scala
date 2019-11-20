package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import com.amazonaws.services.s3.AmazonS3
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.mets.parsers.MetsXmlParser
import uk.ac.wellcome.storage.s3.S3Config
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

case class MetsData(path: String, version: Int)

class MetsTransformerWorkerService(
                                    msgStream: SQSStream[MetsData],
                                    messageSender: BigMessageSender[SNSConfig,TransformedBaseWork],
                                    s3Client: AmazonS3,
                                    s3Config: S3Config
                                  )(implicit ec: ExecutionContext)
    extends Runnable with Logging{

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.foreach(
      className,
      processAndLog
    )

  def processAndLog(metsData: MetsData): Future[Unit] = process(metsData).recover{
    case t =>
      error(s"There was an error processing $metsData: ",t)
      throw t
  }

  private def process(metsData: MetsData) = {
    for {
      metsXmlInputStream <- fetchMets(metsData)
      mets <- Future.fromTry(MetsXmlParser(metsXmlInputStream).toTry)
      work <- Future.fromTry(mets.toWork(metsData.version).toTry)
      _ <- Future.fromTry(messageSender.sendT(work))
    } yield ()
  }

  private def fetchMets(metsData: MetsData) = {
    Future {
        s3Client.getObject(s3Config.bucketName, metsData.path).getObjectContent
    }
  }
}
