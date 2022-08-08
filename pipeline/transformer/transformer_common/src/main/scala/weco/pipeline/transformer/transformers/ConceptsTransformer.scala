package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.parse.PeriodParser
import weco.pipeline.transformer.text.TextNormalisation._

import java.text.Normalizer

trait ConceptsTransformer {

  def newIdIfNeeded[State](
    currentState: State,
    label: String,
    replacementState: Option[IdState.Identifiable] = None,
    ontologyType: String = "Concept"
  ): IdState.Identifiable =
    currentState match {
      case currentAsIdentifiable: IdState.Identifiable => currentAsIdentifiable
      case _                                           =>
        // Normalisation is required here for both case and ascii folding.
        // With label-derived ids, case is (probably rightly) inconsistent, as the subfields
        // are intended to be concatenated, e.g. "History, bananas" and "Bananas, history"
        // In some cases, we see the names being shown in different forms in different fields.
        // e.g. in b24313270, (Memoirs of the Cardinal de Retz,)
        // The Cardinal in question (Author: Retz, Jean François Paul de Gondi de, 1613-1679.) wrote about himself
        // (Subject: Retz, Jean Francois Paul de Gondi de, 1613-1679.)
        val normalizedLabel = Normalizer
          .normalize(
            label.toLowerCase,
            Normalizer.Form.NFKD
          )
          .replaceAll("[^\\p{ASCII}]", "")
          .trim
        replacementState.getOrElse(
          IdState.Identifiable(
            SourceIdentifier(
              identifierType = IdentifierType.LabelDerived,
              ontologyType = ontologyType,
              value = normalizedLabel
            )))
    }

  implicit class AgentOps[State](a: Agent[State]) {
    def normalised: Agent[State] =
      a.copy(label = a.label.trimTrailing(','))
  }

  implicit class ConceptOps[State](c: Concept[State]) {
    def normalised: Concept[State] =
      c.copy(label = c.label.trimTrailingPeriod)

    def identifiable(idState: Option[IdState.Identifiable] = None)
      : Concept[IdState.Identifiable] =
      c.copy(id = newIdIfNeeded(c.id, c.label, idState))
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

    def identifiable(idState: Option[IdState.Identifiable] = None)
      : Meeting[IdState.Identifiable] =
      m.copy(
        id = newIdIfNeeded(m.id, m.label, idState, ontologyType = "Meeting"))
  }

  implicit class OrganisationOps[State](o: Organisation[State]) {
    def normalised: Organisation[State] =
      o.copy(label = o.label.trimTrailing(','))

    def identifiable(idState: Option[IdState.Identifiable] = None)
      : Organisation[IdState.Identifiable] =
      o.copy(id = newIdIfNeeded(o.id, o.label, idState, "Organisation"))
  }

  implicit class PersonOps[State](p: Person[State]) {
    def normalised: Person[State] =
      p.copy(label = p.label.trimTrailing(','))

    def identifiable(idState: Option[IdState.Identifiable] = None)
      : Person[IdState.Identifiable] =
      p.copy(id = newIdIfNeeded(p.id, p.label, idState, "Person"))

  }

  implicit class PlaceOps[State](pl: Place[State]) {
    def normalised: Place[State] =
      pl.copy(label = pl.label.trimTrailing(':'))

    def identifiable(idState: Option[IdState.Identifiable] = None)
      : Place[IdState.Identifiable] =
      pl.copy(id = newIdIfNeeded(pl.id, pl.label, idState, "Place"))
  }

  implicit class PeriodOps[State](p: Period[State]) {
    def identifiable(idState: Option[IdState.Identifiable] = None)
      : Period[IdState.Identifiable] =
      // The label of a period may contain superfluous content that doesn't change the referent of the period
      // e.g. years duplicated in Roman, punctuation, diverse renditions of the term "floruit".
      // The id is therefore based on that "preprocessed" label, rather than simply passing through the
      // label as written.
      p.copy(
        id = newIdIfNeeded(
          p.id,
          PeriodParser.preprocess(p.label),
          idState,
          "Period"))
  }
}
