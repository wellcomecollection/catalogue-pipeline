package weco.pipeline.transformer.tei.transformers

import cats.instances.either._
import cats.syntax.traverse._
import weco.catalogue.internal_model.identifiers.IdState.{Identifiable, Unminted}
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
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
          res <- createContributor(label = label, id = id, isFihrist = isFihrist, ContributionRole("author"))
        } yield res

      }
      .toList
      .sequence

  /**
   * Scribes appear in the physical description section of the manuscript and can appear
   * within a handNote tag with attribute "scribe" as in this example
   * <physDesc>
   *     <handDesc>
   *         <handNote scope="minor" scribe="Scribe_A"> <locus target="#Wellcome_Batak_36801_1 #Wellcome_Batak_36801_2" >a 2-62, b 2-7; b 8-22</locus> Gorak-gorahan, etc. Southern form of ta.</handNote>
   *         <handNote scope="minor" scribe="Scribe_B"> <locus target="#Wellcome_Batak_36801_3">b 23-35</locus>Another hand, which uses the northern ta.</handNote>
   *         <handNote scope="minor" scribe="Scribe_C"> <locus target="#Wellcome_Batak_36801_9">b 44-45</locus>Another hand; careless writing, at first with northern ta, later southern ta.</handNote>
   *         <handNote scope="minor" scribe="Scribe_D"> <locus target="#Wellcome_Batak_36801_10">b 45-56</locus>A different handwriting, using the southern form of ta but not the peculiar form of ya which is found in the text written by <persName>Datu Poduwon</persName>.</handNote>
   *     </handDesc>
   * </physDesc>
   *
   * or within a persName tag with role=scr inside the handNote tag
   * <physDesc>
   *    <handDesc>
   *        <handNote scope="sole">
   *            <persName role="scr">Mahādeva Pāṇḍe</persName>
   *        </handNote>
   *    </handDesc>
   *</physDesc>
   * If the scribe refers to a part or item of the manuscript, the handNote tag will have a locus node with the item or part ids that it refers to
   *
   */
  def scribes(xml: Elem, workId: String)
  : Result[Map[String, List[Contributor[Unminted]]]] =
    (xml \\ "physDesc" \ "handDesc" \ "handNote").foldLeft(
      Right(Map.empty): Result[Map[String, List[Contributor[Unminted]]]]
    ) {
      case (Left(err), _) => Left(err)
      case (Right(scribesMap), node) =>
        for {
          contributor <- getScribeContributor(node)
        } yield  mapContributorToWorkId(workId, node, contributor, scribesMap)
    }


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


  private def getScribeContributor(n: Node) = for{
      maybeLabel <-parseScribeLabel(n)
      contributor <- maybeLabel.map { label =>
        createContributor(label, ContributionRole("scribe"))
      }.sequence
    } yield contributor

  /**
   * If the scribe refers to a nested part or item, it will have a locus tag with a target attribute like:
   * <handNote scope="minor" scribe="Scribe_A"> <locus target="#Wellcome_Batak_36801_1 #Wellcome_Batak_36801_2" >a 2-62, b 2-7; b 8-22</locus> Gorak-gorahan, etc. Southern form of ta.</handNote>
   *
   * If the scribe refers to the wrapper work then it has no locus tag.
   * Here we extract the id of the work the scribe refers to and add it to a map workId -> contributors
   */
  private def mapContributorToWorkId(workId: String, n: Node, maybeContributor: Option[Contributor[Unminted]], scribesMap: Map[String,List[Contributor[Unminted]] ]) = {
    maybeContributor match {
      case None => scribesMap
      case Some(contributor) =>
        val workIds = extractNestedWorkIds(n)
        workIds match {
          case Nil =>
            scribesMap + addIdToMap(workId, scribesMap, contributor)
          case ids =>
            scribesMap ++ addIdsToMap(scribesMap, contributor, ids)

        }

    }
  }

  /**
   * The scribe name can be within the handNote tag directly or within a
   * persName node with role="src" inside the handNote tag. If there is a persName tag,
   * we pick that to construct the scribe name. Otherwise we use the text directly inside the handNote tag
   */
  private def parseScribeLabel(n: Node) = {
    val persNameNodes =
      (n \ "persName").filter(n => (n \@ "role") == "scr").toList
    (n.attribute("scribe"), persNameNodes) match {
      case (Some(_), Nil) => parseLabelFromHandNode(n)
      case (_, List(persName)) => Right(Some(persName.text.trim))
      case (_, list@_ :: _) => getFromOriginalPersNode(n, list).map { case (label, _) => Some(label) }
      case (None, Nil) => Right(None)
    }
  }

  /** Very annoingly if the scribe is in this form:
   *  <handNote scope="minor" scribe="Scribe_A"> <locus target="#Wellcome_Batak_36801_1 #Wellcome_Batak_36801_2" >a 2-62, b 2-7; b 8-22</locus> Gorak-gorahan, etc. Southern form of ta.</handNote>
   *  we have to get the text of the handNote node without getting the text of the locus node.
   *  We do this by parsing only the direct children of handNote that are text nodes.
   * For some reason that I can't understand, this only works if we load the handNode
   * as a XML
   */
  private def parseLabelFromHandNode(n: Node) = {
    Right(Some(
      XML
        .loadString(n.toString())
        .child
        .collect {
          case t: Text => t.text
        }
        .mkString
        .trim
    ))
  }

  private def addIdsToMap(scribesMap: Map[String, List[Contributor[Unminted]]], c: Contributor[Unminted], nodeIds: List[String]) = {
    nodeIds
      .map(
        id =>
          addIdToMap(id, scribesMap, c)
      )
      .toMap
  }

  private def addIdToMap(workId: String, scribesMap: Map[String, List[Contributor[Unminted]]], c: Contributor[Unminted]) = {
    scribesMap.get(workId) match {
      case None =>
        (workId, List(c))
      case Some(list) => (workId, list :+ c)
    }
  }

  private def extractNestedWorkIds(n: Node) = {
    (n \ "locus")
      .map { locus =>
        (locus \@ "target").trim.replaceAll("#", "").split(" ")
      }
      .toList
      .flatten
  }


  private def createContributor(label: String, role: ContributionRole): Result[Contributor[Unminted]] =
    createContributor(label = label, id = "", isFihrist = false, role = role)

  private def createContributor(label: String, id: String, isFihrist: Boolean, role: ContributionRole): Result[Contributor[Unminted]] =
    (label, id) match {
      case (l, _) if l.isEmpty =>
        Left(new RuntimeException(s"The contributor label is empty!"))
      case (l, id) if id.isEmpty =>
        Right(Contributor(Person(l), List(role)))
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
            List(role)
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

}
