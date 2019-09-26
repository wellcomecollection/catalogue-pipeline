package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

object SierraCredits extends SierraTransformer with MarcUtils {

  type Output = Option[String]

  val creditsVarFields = List("508", "511")

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    creditsVarFields
      .flatMap(getMatchingVarFields(bibData, _))
      .flatMap(getSubfieldContents(_, Some("a"))) match {
        case Nil => None
        case strings => Some(strings.mkString("\n"))
      }
      
}
