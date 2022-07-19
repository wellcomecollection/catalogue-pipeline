package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.catalogue.internal_model.identifiers.HasId

trait HasIdMatchers {
  def sourceIdentifier(ontologyType: String,
                       value: String,
                       identifierType: IdentifierType)
    : HavePropertyMatcher[HasId[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasIdentifier(
      ontologyType: String,
      value: String,
      identifierType: IdentifierType)
}

object HasIdMatchers extends HasIdMatchers
