package weco.pipeline.transformer.tei.transformers

import cats.instances.either._
import cats.syntax.traverse._
import weco.catalogue.internal_model.identifiers.IdState.{
  Identifiable,
  Unidentifiable,
  Unminted
}
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  ContributionRole,
  Contributor,
  Person
}
import weco.pipeline.transformer.result.Result

import scala.xml.{Elem, Node, Text, XML}

object TeiContributors {

  /**
   * author nodes appear only within msItem. They contain the name of the Author of the work and optionally the id of the Author.
   * The Id of the Author refers to two different authorities depending on whether the work is an Arabic manuscript or not
   */
  def authors(
               node: Node,
               isFihrist: Boolean
             ): Result[List[Contributor[IdState.Unminted]]] =
    (node \ "author")
      .map { n =>
        for {
          authorInfo <- getLabelAndId(n)
          (label, id) = authorInfo
          res <- createContributor(
            label = label,
            id = id,
            isFihrist = isFihrist
          )
        } yield res

      }
      .toList
      .sequence

  /**
   * Author nodes can be in 2 forms:
   * <msItem xml:id="MS_Arabic_1-item1">
   *  <author key="person_97166546">
   *    <persName xml:lang="en">Avicenna, d. 980-1037
   *    </persName>
   *    <persName xml:lang="ar" type="original">ابو على الحسين ابن عبد الله ابن
   *    سينا</persName>
   *  </author>
   *  or
   * <msItem n="1" xml:id="MS_MSL_114_1">
   *  <author key="person_84812936">Paul of Aegina</author>
   *  So we must check for the existence of the internal persName nodes to decide
   *  where to get the label and id from
   */
  private def getLabelAndId(n: Node) = (n \ "persName").toList match {
    case Nil            => getFromAuthorNode(n)
    case List(persNode) => getFromPersNode(n, persNode)
    case list           => getFromOriginalPersNode(n, list)
  }

  private def createContributor(label: String, id: String, isFihrist: Boolean) =
    (label, id) match {
      case (l, _) if l.isEmpty =>
        Left(new RuntimeException(s"The author label is empty!"))
      case (l, id) if id.isEmpty =>
        Right(Contributor(Person(l), List(ContributionRole("author"))))
      case (l, id) =>
        // If the manuscript is part of the Fihrist catalogue,
        // the ids of authors refer to the Fihrist authority: https://github.com/fihristorg/fihrist-mss/tree/master/authority
        // Otherwise they refer to the VIAF authority files: https://viaf.org/
        val identifierType =
          if (isFihrist) IdentifierType.Fihrist else IdentifierType.VIAF
        Right(
          Contributor(
            Person(
              label = l,
              id = Identifiable(SourceIdentifier(identifierType, "Person", id))
            ),
            List(ContributionRole("author"))
          )
        )
    }

  private def getFromOriginalPersNode(n: Node, list: List[Node]) =
    list.filter(_ \@ "type" == "original") match {
      case List(persNode) => getFromPersNode(n, persNode)
      case Nil =>
        Left(
          new RuntimeException(
            s"No persName nodes with type=original in author $n"
          )
        )
      case _ =>
        Left(
          new RuntimeException(
            s"Multiple persName nodes with type=original in author $n"
          )
        )
    }

  private def getFromPersNode(n: Node, persNode: Node) =
    Right((persNode.text.trim, getId(n, persNode)))

  private def getFromAuthorNode(n: Node) =
    Right((n.text.trim, (n \@ "key").trim))

  /**
   * Sometimes the id of the author is on the persName node
   * and sometimes it is on the wrapping author node and we must deal with both cases.
   */
  private def getId(n: Node, persNode: Node) = {
    val persNodeId = (persNode \@ "key").trim
    if (persNodeId.isEmpty) {
      (n \@ "key").trim
    } else {
      persNodeId
    }
  }

  def scribes(xml: Elem, workId: String)
    : Map[String, List[Contributor[Unminted]]] =
    (xml \\ "physDesc" \ "handDesc" \ "handNote").foldLeft(
      Map.empty: Map[String, List[Contributor[Unminted]]]
    ) {
      case (scribesMap, n: Node) =>
        val nodeIds = (n \ "locus")
          .map { locus =>
            (locus \@ "target").trim.replaceAll("#", "").split(" ")
          }
          .toList
          .flatten
        val persNameNodes =
          (n \ "persName").filter(n => (n \@ "role") == "scr").toList

        val label = (n.attribute("scribe"), persNameNodes) match {
          case (Some(_), Nil) =>
            Some(
              XML
                .loadString(n.toString())
                .child
                .collect {
                  case t: Text => t.text
                }
                .mkString
                .trim
            )
          case (_, head :: _) => Some(head.text.trim)
          case (None, Nil)    => None
        }
        val maybeContributors = label.map{l => List(
          Contributor(
            Unidentifiable,
            Person(l),
            List(ContributionRole("scribe"))
          ))}
        maybeContributors match {
          case Some(c) =>
            nodeIds match {
              case Nil =>
                val bu = scribesMap.get(workId) match {
                  case None =>
                    (workId, c)
                  case Some(list) => (workId, list ++ c)
                }
                scribesMap + bu
              case nodeIds =>
                scribesMap ++ nodeIds
                  .map(
                    id =>
                      scribesMap.get(id) match {
                        case None =>
                          (id, c)
                        case Some(list) => (id, list ++ c)
                      }
                  )
                  .toMap

            }
          case None => scribesMap
        }
    }

}
