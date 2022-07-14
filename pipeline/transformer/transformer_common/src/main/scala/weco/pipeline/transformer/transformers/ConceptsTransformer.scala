package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.text.TextNormalisation._

trait ConceptsTransformer {
  implicit class AgentOps[State](a: Agent[State]) {
    def normalised: Agent[State] =
      a.copy(label = a.label.trimTrailing(','))
  }

  implicit class ConceptOps[State](c: Concept[State]) {
    def normalised: Concept[State] =
      c.copy(label = c.label.trimTrailingPeriod)

    def identifiable(idState: Option[IdState.Identifiable] = None): Concept[IdState.Identifiable] =
      c.copy(id=idState.getOrElse(IdState.Identifiable(
        SourceIdentifier(
          identifierType = IdentifierType.LabelDerived,
          ontologyType = "Concept",
          value = c.label
        ))
      ))
  }

  implicit class GenreOps[State](g: Genre[State]) {
    def normalised: Genre[State] = {
      val normalisedLabel =
        g.label
          .stripSuffix(".")
          .trim
          .replace("Electronic Books", "Electronic books")

      g.copy(label = normalisedLabel)
    }
  }

  implicit class MeetingOps[State](m: Meeting[State]) {
    def normalised: Meeting[State] =
      m.copy(label = m.label.trimTrailing(','))
  }

  implicit class OrganisationOps[State](o: Organisation[State]) {
    def normalised: Organisation[State] =
      o.copy(label = o.label.trimTrailing(','))
  }

  implicit class PersonOps[State](p: Person[State]) {
    def normalised: Person[State] =
      p.copy(label = p.label.trimTrailing(','))
  }

  implicit class PlaceOps[State](pl: Place[State]) {
    def normalised: Place[State] =
      pl.copy(label = pl.label.trimTrailing(':'))

    def identifiable(idState: Option[IdState.Identifiable] = None): Place[IdState.Identifiable] =
      pl.copy(id=idState.getOrElse(IdState.Identifiable(
        SourceIdentifier(
          identifierType = IdentifierType.LabelDerived,
          ontologyType = "Concept",
          value = pl.label
        )
      )))

  }

  implicit class PeriodOps[State](p: Period[State]) {

    def identifiable(idState: Option[IdState.Identifiable] = None): Period[IdState.Identifiable] =
      p.copy(id=idState.getOrElse(IdState.Identifiable(
        SourceIdentifier(
          identifierType = IdentifierType.LabelDerived,
          ontologyType = "Concept",
          value = p.label
        ))
      ))
  }
}
