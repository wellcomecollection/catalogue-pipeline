package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.identifiers.IdState.{Identifiable, Unidentifiable}
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, Node}

object TeiSubjects {
  def apply(xml: Elem): List[Subject[IdState.Unminted]] = (xml \\ "profileDesc" \\ "keywords").flatMap { keywords =>
    val identifierType = (keywords \@ "scheme").toLowerCase.trim match {
      case s if s == "#lcsh" => Some(IdentifierType.LCSubjects)
      case s if s == "#mesh" => Some(IdentifierType.MESH)
      case _ => None
    }
    (keywords \\ "term" ).flatMap { term =>
      val label = NormaliseText(term.text)
      val reference = parseReference(term)
      val id = (reference, identifierType) match {
        case (Some(r), Some(identifierType)) => Identifiable(SourceIdentifier(identifierType, "Subject", r))
        case _ => Unidentifiable
      }
      label.map(l => Subject(id = id, label = l, concepts = List(Concept(label = l))))
    }
  }.toList

  private def parseReference(term: Node) = {
    val referenceString = term \@ "ref"
    val parsedString = referenceString.replaceAll("subject_", "").replaceAll(" ", "")
    NormaliseText(parsedString)
  }
}
