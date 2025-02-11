package weco.pipeline.id_minter.database

import weco.pipeline.id_minter.config.builders.RDSBuilder
import weco.pipeline.id_minter.config.models.{
  IdentifiersTableConfig,
  RDSClientConfig
}
import weco.pipeline.id_minter.models.IdentifiersTable
import weco.pipeline.id_minter.steps.IdentifierGenerator

object RDSIdentifierGenerator {
  def apply(
    rdsClientConfig: RDSClientConfig,
    identifiersTableConfig: IdentifiersTableConfig
  ) = {
    RDSBuilder.buildDB(rdsClientConfig)

    val tableProvisioner = new TableProvisioner(
      rdsClientConfig = rdsClientConfig
    )

    tableProvisioner.provision(
      database = identifiersTableConfig.database,
      tableName = identifiersTableConfig.tableName
    )

    new IdentifierGenerator(
      identifiersDao = new IdentifiersDao(
        identifiers = new IdentifiersTable(
          identifiersTableConfig = identifiersTableConfig
        )
      )
    )
  }
}
