package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.pipeline.transformer.text.TextNormalisation._

trait MiroSubjects extends LabelDerivedIdentifiers {

  /* Populate the subjects field.  This is based on two fields in the XML,
   *  <image_keywords> and <image_keywords_unauth>.  Both of these were
   *  defined in part or whole by the human cataloguers, and in general do
   *  not correspond to a controlled vocabulary.  (The latter was imported
   *  directly from PhotoSoft.)
   *
   *  In some cases, these actually do correspond to controlled vocabs,
   *  e.g. where keywords were pulled directly from Sierra -- but we don't
   *  have enough information in Miro to determine which ones those are.
   */
  def getSubjects(miroRecord: MiroRecord): List[Subject[IdState.Unminted]] = {
    val keywords: List[String] = miroRecord.keywords.getOrElse(List())

    val keywordsUnauth: List[String] =
      miroRecord.keywordsUnauth match {
        case Some(maybeKeywords) => maybeKeywords.flatten
        case None                => List()
      }

    (keywords ++ keywordsUnauth).map {
      keyword =>
        val normalisedLabel = keyword.sentenceCase
        Subject(
          id = identifierFromText(normalisedLabel, ontologyType = "Subject"),
          label = normalisedLabel,
          concepts = List(Concept(normalisedLabel))
        )
    }
  }
}
