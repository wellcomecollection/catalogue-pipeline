package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.identifiers.IdState.{Identifiable, Unidentifiable}
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, Node}

object TeiSubjects {
  /**
   * Subjects live in the profileDesc block of the tei which looks like this:
   * <profileDesc>
   *   <textClass>
   *     <keywords scheme="#LCSH">
   *       <list>
   *         <item>
   *           <term ref="subject_sh85083116">Medicine, Arab</term>
   *         </item>
   *       </list>
   *     </keywords>
   *   </textClass>
   * </profileDesc>
   *
   */
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
    // arabic manuscripts seem to have the subject id in the
    // attribute "key" instead of "ref" ¯\_(ツ)_/¯
    val idValue = if(referenceString.isEmpty)term\@"key" else referenceString
    // some of the subject ids are prepended with "subject_sh" for lcsh or " subject" for mesh.
    // So here we remove that to get the id
    val parsedString = idValue.replaceAll("subject_", "").replaceAll(" ", "")
    NormaliseText(parsedString)
  }
}
