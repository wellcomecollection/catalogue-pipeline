package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, Node}

object TeiSubjects extends LabelDerivedIdentifiers {

  /** Subjects live in the profileDesc block of the tei which looks like this:
    * <profileDesc> <textClass> <keywords scheme="#LCSH"> <list> <item> <term
    * ref="subject_sh85083116">Medicine, Arab</term> </item> </list> </keywords>
    * </textClass> </profileDesc>
    */
  def apply(xml: Elem): List[Subject[IdState.Unminted]] =
    (xml \\ "profileDesc" \\ "keywords").flatMap {
      keywords =>
        (keywords \\ "term").flatMap {
          term =>
            val maybeLabel = NormaliseText(term.text)

            maybeLabel.map(
              label => {
                val reference = parseReference(term)
                val id = createIdentifier(keywords, reference, label)

                Subject(
                  id = id,
                  label = label,
                  concepts = List(Concept(label))
                )
              }
            )
        }
    }.toList

  private def createIdentifier(
    keywords: Node,
    reference: Option[String],
    label: String
  ): IdState.Unminted = {
    val identifierType = (keywords \@ "scheme").toLowerCase.trim match {
      case s if s == "#lcsh" => Some(IdentifierType.LCSubjects)
      case s if s == "#mesh" => Some(IdentifierType.MESH)
      case _                 => None
    }

    (reference, identifierType) match {
      case (Some(value), Some(identifierType)) =>
        IdState.Identifiable(
          sourceIdentifier = SourceIdentifier(
            identifierType = identifierType,
            ontologyType = "Concept",
            value = value
          )
        )
      case _ =>
        identifierFromText(label, ontologyType = "Concept")
    }
  }

  /** Extract the identifier from a term. * The relevant attributes are those in
    * the att.canonical group
    * https://tei-c.org/release/doc/tei-p5-doc/en/html/ref-att.canonical.html
    *
    * `key` should contain a "coded value of some kind" and `ref` should contain
    * one or more URIs to locate the full definition.
    *
    * However, in practice, these have been mostly used interchangeably. As
    * such, we prefer to return the key, which is expected to contain an
    * "externally-defined string identifying the referent"
    */
  private def parseReference(term: Node) = {
    val referenceString = term \@ "ref"
    // arabic manuscripts seem to have the subject id in the
    // attribute "key" instead of "ref" ¯\_(ツ)_/¯
    val keyString = term \@ "key"
    val idValue =
      if (keyString.isEmpty) referenceString else keyString
    // some of the subject ids are prepended with "subject_sh" for lcsh or " subject_" for mesh.
    // So here we remove the prepended "subject_".
    // We also remove any spaces because sometimes some ids look like "sh 1234567"
    val parsedString = idValue.replaceAll("subject_", "").replaceAll(" ", "")
    NormaliseText(parsedString)
  }
}
