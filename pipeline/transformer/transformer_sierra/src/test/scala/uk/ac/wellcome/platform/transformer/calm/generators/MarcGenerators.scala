package uk.ac.wellcome.platform.transformer.calm.generators

import uk.ac.wellcome.platform.transformer.calm.source.{MarcSubfield, VarField}

trait MarcGenerators {
  def createVarFieldWith(marcTag: String,
                         indicator2: Option[String] = None,
                         subfields: List[MarcSubfield] = List(),
                         content: Option[String] = None): VarField =
    VarField(
      marcTag = Some(marcTag),
      content = content,
      indicator1 = None,
      indicator2 = indicator2,
      subfields = subfields
    )

  def createVarFieldWith(marcTag: String,
                         indicator2: String,
                         subfields: List[MarcSubfield]): VarField =
    createVarFieldWith(
      marcTag = marcTag,
      indicator2 = Some(indicator2),
      subfields = subfields
    )
}
