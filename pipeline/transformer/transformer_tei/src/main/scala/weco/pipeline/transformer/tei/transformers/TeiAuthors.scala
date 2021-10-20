package weco.pipeline.transformer.tei.transformers

import cats.instances.either._
import cats.syntax.traverse._
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.result.Result

import scala.xml.Node

object TeiAuthors {
  def apply(node: Node, isFihirist: Boolean): Result[List[Contributor[IdState.Unminted]]] = {
    val seq:  List[Either[Throwable,Contributor[IdState.Unminted]]] = (node \ "author").map { n =>
      for {
        labelId <- getLabelAndId(n)
        (label, id) = labelId
        res<-(label, id) match {
          case (l,_) if l.isEmpty => Left(new RuntimeException(s"The author label in node $n is empty!"))
          case (l, id) if id.isEmpty => Right(Contributor(Person(l), List(ContributionRole("author"))))
          case (l,id) =>
            val identifierType = if(isFihirist)IdentifierType.Fihrist else IdentifierType.VIAF
            Right(Contributor(Person(label = l, id = Identifiable(SourceIdentifier(identifierType, "Person", id))), List(ContributionRole("author"))))
        }
      }yield res

    }.toList
    seq.sequence
  }

  private def getLabelAndId(n: Node) = (n \ "persName").toList match {
    case Nil => Right((n.text.trim, (n \@ "key").trim))
    case List(persNode) =>
      val id: String = getId(n, persNode)
      Right((persNode.text.trim, id))
    case list =>
      list.filter(_ \@ "type" == "original") match {
        case List(persNode) => Right((persNode.text.trim, getId(n, persNode)))
        case Nil => Left(new RuntimeException(s"No persName nodes with type=original in author $n"))
        case _ => Left(new RuntimeException(s"Multiple persName nodes with type=original in author $n"))
      }
  }

  private def getId(n: Node, persNode: Node) = {
    val persNodeId = (persNode \@ "key").trim
    if (persNodeId.isEmpty) {
      (n \@ "key").trim
    } else {
      persNodeId
    }
  }
}
