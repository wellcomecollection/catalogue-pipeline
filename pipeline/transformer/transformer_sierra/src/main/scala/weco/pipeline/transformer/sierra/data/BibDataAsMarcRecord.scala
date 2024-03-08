package weco.pipeline.transformer.sierra.data

import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcRecord,
  MarcSubfield
}
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.{Subfield, VarField}

trait SierraMarcDataConversions {
  import scala.language.implicitConversions

  /* TODO:
   * This implicit conversion stuff exists to temporarily implement SierraBibData
   * as a subclass of MarcRecord until I move it out of the scala-libs project
   * work out exactly what we _do_ need it to look like, and implement the
   * interface directly on it.
   * */
  implicit def bibDataToMarcRecord(bibData: SierraBibData): MarcRecord =
    new BibDataAsMarcRecord(bibData)

  implicit def varFieldToMarcField(varField: VarField): MarcField =
    MarcField(
      marcTag = varField.marcTag.get,
      subfields = varField.subfields.map(sierraSubfieldToMarcSubField),
      content = None,
      fieldTag = varField.fieldTag,
      indicator1 = varField.indicator1.getOrElse(" "),
      indicator2 = varField.indicator2.getOrElse(" ")
    )

  implicit def sierraSubfieldToMarcSubField(subfield: Subfield): MarcSubfield =
    MarcSubfield(tag = subfield.tag, content = subfield.content)
}
object SierraMarcDataConversions extends SierraMarcDataConversions {}

class BibDataAsMarcRecord(bibData: SierraBibData)
    extends MarcRecord
    with SierraQueryOps {

  override def fieldsWithTags(tags: String*): Seq[MarcField] =
    bibData
      .varfieldsWithTags(tags: _*)
      .map(SierraMarcDataConversions.varFieldToMarcField)

}
