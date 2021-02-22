package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import weco.catalogue.sierra_adapter.linker.SierraLinkerWorkerService

class SierraItemsToDynamoWorkerService[Destination](
                                                     sqsStream: SQSStream[NotificationMessage],
                                                     itemLinkStore: ItemLinkingRecordStore,
                                                     messageSender: MessageSender[Destination]
) extends SierraLinkerWorkerService(sqsStream, itemLinkStore, messageSender)
