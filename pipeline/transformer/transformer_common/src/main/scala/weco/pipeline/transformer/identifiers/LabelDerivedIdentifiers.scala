package weco.pipeline.transformer.identifiers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.text.TextNormalisation._

import java.text.Normalizer

trait LabelDerivedIdentifiers {

  /** Create a label-derived identifier for a label.
    *
    * Normalisation is required here for both case and ascii folding. With
    * label-derived ids, case is (probably rightly) inconsistent, as the
    * subfields are intended to be concatenated, e.g. "History, bananas" and
    * "Bananas, history" In some cases, we see the names being shown in
    * different forms in different fields. e.g. in b24313270, (Memoirs of the
    * Cardinal de Retz,) The Cardinal in question (Author: Retz, Jean François
    * Paul de Gondi de, 1613-1679.) wrote about himself (Subject: Retz, Jean
    * Francois Paul de Gondi de, 1613-1679.)
    */
  def identifierFromText(
    label: String,
    ontologyType: String
  ): IdState.Identifiable = {
    val normalizedLabel = Normalizer
      .normalize(
        label.trimTrailingPeriod.trim.toLowerCase,
        Normalizer.Form.NFKD
      )
      .replaceAll("[^\\p{ASCII}]", "")
      .trim

    // We have a handful of works that need label-derived identifiers which are
    // longer than the 'SourceId' column in the ID minter database.
    //
    // (Here "a handful" is approximately two dozen as of September 2022, and within
    // that set it looks like there are some duplicate labels.)
    //
    // These labels aren't meant to be human-readable, and truncating is easier than
    // making the ID minter handle extreme edge cases.
    val truncatedLabel = normalizedLabel
      .slice(0, 255)
      .trim

    IdState.Identifiable(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = truncatedLabel,
        ontologyType = ontologyType
      )
    )
  }
}
