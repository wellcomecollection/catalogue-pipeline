package uk.ac.wellcome.calm_adapter

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {

  runWithConfig { config =>
    new CalmAdapterWorkerService()
  }
}
