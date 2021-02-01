package uk.ac.wellcome.platform.calm_deletion_checker

import uk.ac.wellcome.platform.calm_api_client.CalmRetriever
import weco.catalogue.source_model.CalmSourcePayload

import scala.concurrent.Future

class DeletionChecker(calmRetriever: CalmRetriever) {

  def deletedRecords(
    batch: Seq[CalmSourcePayload]): Future[Set[CalmSourcePayload]] =
    ???

}
