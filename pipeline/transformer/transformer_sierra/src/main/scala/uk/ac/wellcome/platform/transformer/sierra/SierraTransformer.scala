package uk.ac.wellcome.platform.transformer.sierra

import grizzled.slf4j.Logging

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraTransformer extends Logging {

  type Output

  lazy val transformerName = this.getClass.getSimpleName.dropRight(1)

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output = {
    val output = transform(bibId, bibData)
    info(s"DATA TRANSFORMED: BibID=${bibId}, Transformer=${transformerName}, Output=${output}")
    output
  }

  def transform(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
