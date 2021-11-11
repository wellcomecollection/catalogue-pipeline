package weco.pipeline.transformer.tei.transformers

import cats.instances.either._
import cats.syntax.traverse._
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState.Unminted
import weco.catalogue.internal_model.work.Contributor
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.{TeiData, TeiOps}

import scala.xml.{Elem, Node, NodeSeq}

object TeiNestedData extends Logging {

  /**
    * TEI works can be composed of other works.
    * This function extracts the information about these nested works.
    *
    * Nested works can be specified in TEI as msItem or msPart depending
    * if the manuscript is a single part manuscript or a multipart manuscript.
    * check https://github.com/wellcomecollection/wellcome-collection-tei/blob/main/docs/TEI_Manual_2020_V1.pdf
    * for more info.
    */
  def nestedTeiData(xml: Elem,
                    wrapperTitle: String,
                    scribesMap: Map[String, List[Contributor[Unminted]]]) =
    for {
      catalogues <- getCatalogues(xml)
      nestedItems <- nestedTeiDataFromItems(
        xml = xml,
        wrapperTitle = wrapperTitle,
        catalogues = catalogues,
        nodeSeq = xml \\ "msDesc" \ "msContents",
        scribesMap = scribesMap)
      nestedData <- nestedItems match {
        case Nil =>
          nestedTeiDataFromParts(
            xml = xml,
            wrapperTitle = wrapperTitle,
            catalogues = catalogues,
            scribesMap = scribesMap)
        case itemData => Right(itemData)
      }
    } yield nestedData

  /**
    * Extract information about inner works for multi part manuscripts.
    * Multi part manuscripts have msPart elements containing information about inner works.
    * msParts don't have a title so we construct the title concatenating the
    * title of the wrapper work and the part number.
    */
  private def nestedTeiDataFromParts(
    xml: Elem,
    wrapperTitle: String,
    catalogues: List[String],
    scribesMap: Map[String, List[Contributor[Unminted]]])
    : Result[List[TeiData]] =
    (xml \\ "msDesc" \ "msPart").zipWithIndex
      .map { case (node, i) => (node, i + 1) }
      .map {
        case (node, i) =>
          for {
            id <- TeiOps.getIdFrom(node)
            description <- TeiOps.summary(node)
            languageData <- TeiLanguages.parseLanguages(node \ "msContents")
            (languages, languageNotes) = languageData
            partTitle = s"$wrapperTitle part $i"
            items <- extractLowerLevelItems(
              xml,
              partTitle,
              node \ "msContents",
              catalogues,
              scribesMap)
          } yield {
            TeiData(
              id = id,
              title = partTitle,
              languages = languages,
              notes = languageNotes,
              description = description,
              nestedTeiData = items,
              contributors = scribesMap.getOrElse(id, Nil),
              physicalDescription = TeiPhysicalDescription(node)
            )
          }
      }
      .toList
      .sequence

  /**
    * Extract information about inner works for single part manuscripts.
    * For single part manuscripts, inner works are described in msItem elements.
    */
  private def nestedTeiDataFromItems(
    xml: Elem,
    wrapperTitle: String,
    catalogues: List[String],
    nodeSeq: NodeSeq,
    scribesMap: Map[String, List[Contributor[Unminted]]])
    : Result[List[TeiData]] =
    (nodeSeq \ "msItem").zipWithIndex
    // The indexing starts at zero but we want to count items from 1 so we add 1
      .map { case (node, i) => (node, i + 1) }
      .map {
        case (node, i) =>
          for {
            title <- getTitleForItem(
              node,
              wrapperTitle = wrapperTitle,
              itemNumber = i)
            id <- TeiOps.getIdFrom(node)
            languageData <- TeiLanguages.parseLanguages(node)
            (languages, languageNotes) = languageData
            notes = TeiNotes(node)
            items <- extractLowerLevelItems(
              xml,
              title,
              node,
              catalogues,
              scribesMap)
            authors <- TeiContributors.authors(
              node,
              containsFihrist(catalogues))
          } yield
            TeiData(
              id = id,
              title = title,
              languages = languages,
              notes = languageNotes ++ notes,
              nestedTeiData = items,
              contributors = authors ++ scribesMap.getOrElse(id, Nil))
      }
      .toList
      .sequence

  /**
    * Manuscripts in the Fihrist catalogue - the Arabic manuscripts - are
    * catalogued to a higher level of granularity and it's not necessarily true in this case that a msItem is a work.
    * They are difficult to update to make them more similar to other manuscripts so, for now,
    * we just don't extract lower level items for manuscripts in the Fihrist catalogue.
    */
  private def extractLowerLevelItems(
    xml: Elem,
    partTitle: String,
    nodes: NodeSeq,
    catalogues: List[String],
    scribesMap: Map[String, List[Contributor[Unminted]]])
    : Either[Throwable, List[TeiData]] =
    catalogues match {
      case catalogues if containsFihrist(catalogues) =>
        Right(Nil)
      case _ =>
        nestedTeiDataFromItems(
          xml = xml,
          wrapperTitle = partTitle,
          catalogues = catalogues,
          nodeSeq = nodes,
          scribesMap = scribesMap)
    }

  private def containsFihrist(catalogues: List[String]): Boolean =
    catalogues.exists(_.trim.toLowerCase == "fihrist")

  private def getTitleForItem(itemNode: Node,
                              wrapperTitle: String,
                              itemNumber: Int): Result[String] =
    extractTitleFromItem(itemNode) match {
      case Some(title) => Right(title)
      case None        => Right(constructTitleForItem(wrapperTitle, itemNumber))
    }

  /**
    * In an XML like this:
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
    *  <teiHeader>
    *    <fileDesc>
    *      <publicationStmt>
    *        <idno type="msID">Wellcome Malay 7</idno>
    *        <idno type="catalogue">The Hervey Malay Collection in the Wellcome Institute</idno>
    *        <idno type="catalogue">Catalogue of Malay manuscripts in the Wellcome Institute for the History of Medicine</idno>
    *       </publicationStmt>
    * Extracts the values of idno tags with type "catalogue"
    */
  private def getCatalogues(xml: Elem): Result[List[String]] = {
    val nodes =
      (xml \ "teiHeader" \ "fileDesc" \ "publicationStmt" \ "idno").toList
    val maybeCatalogues = nodes.filter(n => (n \@ "type") == "catalogue")
    maybeCatalogues match {
      case l @ _ :: _ => Right(l.map(_.text))
      case Nil        => Right(Nil)
    }
  }

  /**
    * In an XML like this:
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
    *  <teiHeader>
    *    <fileDesc>
    *      <titleStmt>
    *        <title>Wellcome Library</title>
    *      </titleStmt>
    *      <sourceDesc>
    *        <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *          <msContents>
    *            <msItem xml:id="MS_Arabic_1-item1">
    *              <title xml:lang="ar-Latn-x-lc" key="work_3001">Al-Qānūn fī al-ṭibb</title>
    * extract the title from the msItem, so "Al-Qānūn fī al-ṭibb" in the example.
    */
  private def extractTitleFromItem(itemNode: Node): Option[String] = {
    val titleNodes = (itemNode \ "title").toList
    titleNodes match {
      case List(titleNode) => Some(titleNode.text)
      case list =>
        list.filter(n => (n \@ "type").toLowerCase == "original") match {
          case List(singleNode) => Some(singleNode.text)
          case Nil =>
            warn(s"Cannot find original title in msItem $titleNodes")
            None
          case _ =>
            warn(s"Multiple titles with type original msItem $titleNodes")
            None
        }
    }
  }
  private def constructTitleForItem(wrapperTitle: String,
                                    itemNumber: Int): String =
    s"$wrapperTitle item $itemNumber"
}
