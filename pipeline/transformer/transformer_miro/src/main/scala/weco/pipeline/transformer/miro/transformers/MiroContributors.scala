package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Agent, Contributor}
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
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
    val maybeContributorCreatorLabel = MiroContributorCredit
      .getCredit(miroRecord)
      .flatMap {
        case "Wellcome Collection" => None
        case s                     => Some(s)
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
