package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraUniformTitle extends MarcUtils {

  def getUniformTitle(bibData: SierraBibData): Option[String] =
    getUniformTitleFromField(bibData, "240") orElse getUniformTitleFromField(
      bibData,
      "130")

  private def getUniformTitleFromField(bibData: SierraBibData,
                                       tag: String): Option[String] =
    getMatchingVarFields(bibData, tag).flatMap {
      getSubfieldContents(_, Some("a"))
    }.headOption
}
