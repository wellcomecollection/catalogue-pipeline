package uk.ac.wellcome.messaging.memory

import io.circe.Encoder
import uk.ac.wellcome.messaging.BigMessageSender
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.Codec

class MemoryBigMessageSender[T](
  maxSize: Int = 100,
  storeNamespace: String = "MemoryBigMessageSender",
  messageDestination: String = "MemoryBigMessageSender"
)(
  implicit
  val encoder: Encoder[T],
  codecT: Codec[T]
) extends BigMessageSender[String, T] {

  override val messageSender: MemoryMessageSender = new MemoryMessageSender {
    override val destination: String = messageDestination
  }

  override val objectStore: ObjectStore[T] = new MemoryObjectStore[T]()
  override val namespace: String = storeNamespace
  override val maxMessageSize: Int = maxSize

  def messages: List[messageSender.underlying.MemoryMessage] =
    messageSender.messages
}
