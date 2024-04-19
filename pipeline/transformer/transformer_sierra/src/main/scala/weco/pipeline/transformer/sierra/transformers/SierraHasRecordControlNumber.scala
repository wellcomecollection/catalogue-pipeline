package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.marc_common.transformers.MarcHasRecordControlNumber

trait SierraHasRecordControlNumber extends MarcHasRecordControlNumber {

  // We've seen the following data in subfield $0 which needs to be
  // normalisation:
  //
  //  * The same value repeated multiple times
  //    ['D000056', 'D000056']
  //
  //  * The value repeated with the prefix (DNLM)
  //    ['D049671', '(DNLM)D049671']
  //
  //    Here the prefix is denoting the authority it came from, which is
  //    an artefact of the original Sierra import.  We don't need it.
  //
  //  * The value repeated with trailing punctuation
  //    ['D004324', 'D004324.']
  //
  //  * The value repeated with varying whitespace
  //    ['n  82105476 ', 'n 82105476']
  //
  //  * The value repeated with a MESH URL prefix
  //    ['D049671', 'https://id.nlm.nih.gov/mesh/D049671']
  //
  override protected def normalise(identifier: String): String =
    super
      .normalise(identifier)
      .replaceFirst("^\\(DNLM\\)", "")
      .replaceFirst("^https://id\\.nlm\\.nih\\.gov/mesh/", "")
}
