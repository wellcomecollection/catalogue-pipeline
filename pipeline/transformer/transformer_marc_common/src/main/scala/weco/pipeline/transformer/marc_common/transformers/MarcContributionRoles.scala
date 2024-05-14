package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.work.ContributionRole
import weco.pipeline.transformer.marc_common.models.MarcField

object MarcContributionRoles extends MarcFieldTransformer {
  type Output = Seq[ContributionRole]
  private def roleSubfieldCodes(marcTag: String): Seq[String] =
    marcTag.substring(1) match {
      case "00" => Seq("e", "j")
      case "10" => Seq("e")
      case "11" => Seq("j")
    }

  override def apply(field: MarcField): Seq[ContributionRole] =
    field.subfields
      .filter(
        subfield => roleSubfieldCodes(field.marcTag).contains(subfield.tag)
      )
      .map(_.content)
      // The contribution role in the raw MARC data sometimes includes a
      // trailing full stop, because all the subfields are meant to be concatenated
      // into a single sentence.
      //
      // This full stop doesn't make sense in a structured field, so remove it.
      .map(_.stripSuffix("."))
      .map(ContributionRole)
}
