package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Agent, Contributor}
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.miro.exceptions.MiroTransformerException
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroContributors
    extends MiroContributorCodes
    with LabelDerivedIdentifiers {
  def getContributors(
    miroRecord: MiroRecord): List[Contributor[IdState.Unminted]] = {

    // <image_creator>: the primary creator
    val primaryCreatorLabels = miroRecord.creator
      .getOrElse(List())
      .flatten

    // <image_secondary_creator>: what MIRO calls Secondary Creator, which
    // will also just have to map to our object property "hasCreator"
    val secondaryCreatorLabels = miroRecord.secondaryCreator.getOrElse(List())

    // We also add the contributor code for the non-historical images, but
    // only if the contributor *isn't* Wellcome Collection.
    val maybeContributorCreatorLabel = miroRecord.sourceCode.flatMap { code =>
      lookupContributorCode(miroId = miroRecord.imageNumber, code = code) match {
        case Some("Wellcome Collection") => None
        case Some(s)                     => Some(s)
        case None =>
          throw MiroTransformerException(
            s"Unable to look up contributor credit line for ${miroRecord.sourceCode} on ${miroRecord.imageNumber}"
          )
      }
    }

    val labels = primaryCreatorLabels ++ secondaryCreatorLabels ++ List(
      maybeContributorCreatorLabel).flatten

    labels.map { label =>
      Contributor(
        agent = Agent(
          id = identifierFromText(label, ontologyType = "Agent"),
          label = label,
        ),
        roles = List()
      )
    }
  }
}
