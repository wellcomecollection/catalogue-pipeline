package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.Assertions.fail
import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Subject}

trait SubjectMatchers {

  def labelDerivedSubjectId(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLabelDerivedIdentifier(
      ontologyType = "Subject",
      expectedValue = expectedValue)

  def meshSubjectId(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasMeshIdentifier(
      ontologyType = "Subject",
      expectedValue = expectedValue)

  def lcSubjectsSubjectId(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLCSubjectsIdentifier(
      ontologyType = "Subject",
      expectedValue = expectedValue)

  implicit class SubjectTestOps[State](subject: Subject[State]) {

    /**
      * Return the concept from a Subject that has exactly one concept in its concepts list
      *
      * A common sequence in tests is to process an input that generates a Subject with one concept
      * in order to assert that that concept has been created correctly.
      *
      * Although not strictly a Matcher, this forms part of the test DSL, and will cause a test failure
      * when the Subject's concept list is not as expected.
      */
    def onlyConcept: AbstractRootConcept[State] = {
      try {
        val List(concept) = subject.concepts
        concept
      } catch {
        case _: MatchError =>
          fail(
            s"Subject expected to have exactly one concept, found: ${subject.concepts}")
      }
    }
  }

}

object SubjectMatchers extends SubjectMatchers
