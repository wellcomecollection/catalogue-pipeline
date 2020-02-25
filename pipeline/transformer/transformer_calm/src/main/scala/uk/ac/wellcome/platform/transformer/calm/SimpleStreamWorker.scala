package uk.ac.wellcome.platform.transformer.calm

import akka.NotUsed
import akka.stream.scaladsl.Flow

/* This worker just creates simple flows from functions defined as a convenience
 * for implementations that are using the definition of the flow rather than the
 * ability to control the properties of the different flow e.g. backpressure
 */
trait SimpleStreamWorker[Message, MessageData, SinkData]
    extends StreamWorker[Message, MessageData, SinkData] {

  private type Result[T] = Either[Throwable, T]

  val decodeMessageFlow: Flow[Message, Result[MessageData], NotUsed] =
    Flow.fromFunction(decodeMessage)
  val workFlow: Flow[MessageData, Result[SinkData], NotUsed] =
    Flow.fromFunction(work)
  val doneFlow: Flow[SinkData, Result[Unit], NotUsed] =
    Flow.fromFunction(done)

  def decodeMessage(message: Message): Result[MessageData]
  def work(messageData: MessageData): Result[SinkData]
  def done(sinkData: SinkData): Result[Unit]
}
