package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import com.amazonaws.services.s3.AmazonS3
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
    extends Runnable {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.foreach(
      className,
      process
    )

  def process(metsData: MetsData): Future[Unit] =
    for {
      metsXmlInputStream <- fetchMets(metsData)
      mets <- Future.fromTry(MetsXmlParser(metsXmlInputStream))
      work <- Future.fromTry(mets.toWork(metsData.version))
      _ <- Future.fromTry(messageSender.sendT(work))
    } yield()

  private def fetchMets(metsData: MetsData) = {
    Future {
        s3Client.getObject(s3Config.bucketName, metsData.path).getObjectContent
    }
  }
}
