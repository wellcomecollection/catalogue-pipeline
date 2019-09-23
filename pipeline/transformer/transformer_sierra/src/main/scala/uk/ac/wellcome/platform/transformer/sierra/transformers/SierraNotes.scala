package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

object SierraNotes extends SierraTransformer with MarcUtils {

  type Output = List[String]

  val notesFields = List("500", "501", "504", "518", "536", "545", "547", "562")

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    notesFields
      .flatMap(getMatchingVarFields(bibData, _))
      .flatMap(_.content)
}
