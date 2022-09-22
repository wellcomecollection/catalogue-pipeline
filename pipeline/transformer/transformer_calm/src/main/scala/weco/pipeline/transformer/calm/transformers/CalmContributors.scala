package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Agent, Contributor}
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers

object CalmContributors extends CalmRecordOps with LabelDerivedIdentifiers {
  def apply(record: CalmRecord): List[Contributor[IdState.Unminted]] =
    record.getList("CreatorName").map { name =>
      Contributor(
        agent = Agent(
          id = identifierFromText(
            label = name,
            ontologyType = "Agent"
          ),
          label = name
        ),
        roles = Nil
      )
    }
}
