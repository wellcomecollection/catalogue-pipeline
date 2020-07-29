package uk.ac.wellcome.bigmessaging

import uk.ac.wellcome.messaging.{IndividualMessageSender, MessageSender}

class BigMessageSender[Destination](
  val underlying: IndividualMessageSender[Destination],
  val subject: String,
  val destination: Destination
) extends MessageSender[Destination]
