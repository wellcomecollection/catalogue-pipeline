package uk.ac.wellcome.platform.idminter.services

import akka.Done
import uk.ac.wellcome.platform.idminter.config.models.{IdentifiersTableConfig, RDSClientConfig}
import uk.ac.wellcome.platform.idminter.database.TableProvisioner
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class IdMinterService[Destination](
  workerService: IdMinterWorkerService[Destination],
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig
) extends Runnable {
  def run(): Future[Done] = {
    val tableProvisioner = new TableProvisioner(
      rdsClientConfig = rdsClientConfig
    )

    tableProvisioner.provision(
      database = identifiersTableConfig.database,
      tableName = identifiersTableConfig.tableName
    )

    workerService.run()
  }
}
