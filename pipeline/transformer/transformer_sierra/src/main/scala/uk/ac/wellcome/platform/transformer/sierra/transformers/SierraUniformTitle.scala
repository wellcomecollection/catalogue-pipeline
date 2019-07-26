package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraUniformTitle {

  def getUniformTitle(bibData: SierraBibData): Option[String] = None
}
