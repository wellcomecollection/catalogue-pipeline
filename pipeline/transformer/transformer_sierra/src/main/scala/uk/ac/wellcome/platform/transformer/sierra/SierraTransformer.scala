package uk.ac.wellcome.platform.transformer.sierra

import grizzled.slf4j.Logging

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait Info {
  def info: String
}

trait SierraTransformer extends Logging {

  type Output

  type TransformInfo <: Info

  case class Log(bibId: SierraBibNumber, transformerName: String, transformInfo: TransformInfo, output: Output) {
    override def toString: String =
      "DATA TRANSFORMED: " +
        s"BibID=${bibId}, " +
        s"Transformer=${transformerName}, " +
        s"Info=${transformInfo.info}, " +
        s"Output=${output}"
  }

  lazy val transformerName = this.getClass.getSimpleName.dropRight(1)

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output = {
    val (output, transformInfo) = transform(bibId, bibData)
    info(Log(bibId, transformerName, transformInfo, output))
    output
  }

  def transform(bibId: SierraBibNumber, bibData: SierraBibData): (Output, TransformInfo)
}
