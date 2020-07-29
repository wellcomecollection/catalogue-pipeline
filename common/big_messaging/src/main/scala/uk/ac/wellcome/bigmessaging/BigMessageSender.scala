package uk.ac.wellcome.bigmessaging

import uk.ac.wellcome.messaging.MessageSender

class BigMessageSender[Destination](
  val underlying: IndividualBigMessageSender[Destination],
  val subject: String,
  val destination: Destination
) extends MessageSender[Destination]
