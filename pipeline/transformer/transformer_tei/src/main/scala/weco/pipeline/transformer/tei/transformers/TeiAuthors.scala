package weco.pipeline.transformer.tei.transformers

import cats.instances.either._
import cats.syntax.traverse._
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.result.Result

import scala.xml.Node

object TeiAuthors {
  def apply(node: Node, isFihrist: Boolean): Result[List[Contributor[IdState.Unminted]]] = (node \ "author").map { n =>
      for {
        labelId <- getLabelAndId(n)
        (label, id) = labelId
        res <- createContributor(label = label, id = id, isFihrist = isFihrist)
      }yield res

    }.toList.sequence

  private def getLabelAndId(n: Node) = (n \ "persName").toList match {
    case Nil => getFromAuthorNode(n)
    case List(persNode) => getFromPersNode(n, persNode)
    case list => getFromOriginalPersNode(n, list)
  }

  private def createContributor(label: String, id: String, isFihrist: Boolean) = (label, id) match {
    case (l,_) if l.isEmpty => Left(new RuntimeException(s"The author label is empty!"))
    case (l, id) if id.isEmpty => Right(Contributor(Person(l), List(ContributionRole("author"))))
    case (l,id) =>
      val identifierType = if(isFihrist)IdentifierType.Fihrist else IdentifierType.VIAF
      Right(Contributor(Person(label = l, id = Identifiable(SourceIdentifier(identifierType, "Person", id))), List(ContributionRole("author"))))
  }

  private def getFromOriginalPersNode(n: Node, list: List[Node]) = list.filter(_ \@ "type" == "original") match {
      case List(persNode) => getFromPersNode(n, persNode)
      case Nil => Left(new RuntimeException(s"No persName nodes with type=original in author $n"))
      case _ => Left(new RuntimeException(s"Multiple persName nodes with type=original in author $n"))
    }
  private def getFromPersNode(n: Node, persNode: Node) = Right((persNode.text.trim, getId(n, persNode)))

  private def getFromAuthorNode(n: Node) = Right((n.text.trim, (n \@ "key").trim))

  private def getId(n: Node, persNode: Node) = {
    val persNodeId = (persNode \@ "key").trim
    if (persNodeId.isEmpty) {
      (n \@ "key").trim
    } else {
      persNodeId
    }
  }
}
