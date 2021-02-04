package uk.ac.wellcome.platform.calm_deletion_checker

import uk.ac.wellcome.platform.calm_api_client.CalmApiClient
import weco.catalogue.source_model.CalmSourcePayload

import scala.concurrent.Future

class DeletionChecker(calmApiClient: CalmApiClient) {

  def deletedRecords(
    batch: Seq[CalmSourcePayload]): Future[Set[CalmSourcePayload]] =
    ???

}
