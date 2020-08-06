package uk.ac.wellcome.platform.inference_manager

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.models.work.internal.{Identified, MergedImage, Minted}

import scala.util.Try

package object services {
  type MergedIdentifiedImage = MergedImage[Identified, Minted]

  type MessagePair[T] = (Message, T)

  type RequestPoolFlow[T] =
    Flow[(HttpRequest, MessagePair[T]), (Try[HttpResponse], MessagePair[T]), _]
}
