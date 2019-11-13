package uk.ac.wellcome.mets.services

import akka.stream.ActorMaterializer
import akka.Done
import akka.stream.scaladsl._
import akka.stream.alpakka.sqs.scaladsl._
import akka.stream.alpakka.sns.scaladsl._
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sns.AmazonSNSAsync

import uk.ac.wellcome.messaging.sqs.SQSConfig
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets.models._

import scala.concurrent.Future

case class SNSConfig(topicArn: String)

case class StorageUpdate(space: String, bagId: String)

case class Mets()

class MetsAdaptorWorkerService(
  sqsConfig: SQSConfig,
  snsConfig: SNSConfig,
  bagRetriever: BagRetriever,
  concurrentConnections: Int = 6)(
  implicit
  materializer: ActorMaterializer,
  snsClient: AmazonSNSAsync,
  sqsClient: AmazonSQSAsync)
    extends Runnable {

  def run(): Future[Done] =
    msgSource
      .via(retrieveBag)
      .via(getMetsXml)
      .via(storeMets)
      .toMat(msgSink)(Keep.right)
      .run()

  def msgSource: Source[StorageUpdate, _] =
    SqsSource(sqsConfig.queueUrl)
      .map(msg => fromJson[StorageUpdate](msg.getBody).get)

  def retrieveBag: Flow[StorageUpdate, Bag, _] =
    Flow[StorageUpdate]
      .mapAsync(concurrentConnections) { bagRetriever.getBag }
      .collect { case Some(bag) => bag }

  def getMetsXml: Flow[Bag, Mets, _] =
    throw new NotImplementedError

  def storeMets: Flow[Mets, String, _] =
    throw new NotImplementedError

  def msgSink: Sink[String, Future[Done]] =
    SnsPublisher.sink(snsConfig.topicArn)
}
