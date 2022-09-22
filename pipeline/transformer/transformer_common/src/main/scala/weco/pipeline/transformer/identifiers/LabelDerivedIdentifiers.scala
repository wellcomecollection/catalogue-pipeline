package weco.pipeline.transformer.identifiers

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.pipeline.transformer.text.TextNormalisation._

import java.text.Normalizer

trait LabelDerivedIdentifiers {

  /** Create a label-derived identifier for a label.
   *
   * Normalisation is required here for both case and ascii folding.
   * With label-derived ids, case is (probably rightly) inconsistent, as the subfields
   * are intended to be concatenated, e.g. "History, bananas" and "Bananas, history"
   * In some cases, we see the names being shown in different forms in different fields.
   * e.g. in b24313270, (Memoirs of the Cardinal de Retz,)
   * The Cardinal in question (Author: Retz, Jean François Paul de Gondi de, 1613-1679.) wrote about himself
   * (Subject: Retz, Jean Francois Paul de Gondi de, 1613-1679.)
   *
   */
  def identifierFromText(label: String, ontologyType: String): IdState.Identifiable = {
    val normalizedLabel = Normalizer
      .normalize(
        label.trimTrailingPeriod.trim.toLowerCase,
        Normalizer.Form.NFKD
      )
      .replaceAll("[^\\p{ASCII}]", "")

    IdState.Identifiable(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = normalizedLabel,
        ontologyType = ontologyType
      )
    )
  }
}
