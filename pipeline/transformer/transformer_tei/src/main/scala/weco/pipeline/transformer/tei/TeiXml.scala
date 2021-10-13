package weco.pipeline.transformer.tei

import scala.util.Try
import scala.xml.{Elem, Node, NodeSeq, XML}
import grizzled.slf4j.Logging
import cats.syntax.traverse._
import cats.instances.either._
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.transformers.TeiLanguages
class TeiXml(val xml: Elem) extends Logging {
  val id: String = getIdFrom(xml).getOrElse(
    throw new RuntimeException(s"Could not find an id in XML!"))

  /**
    * All the identifiers of the TEI file are in a `msIdentifier` bloc.
    * We need the `altIdentifier` node where `type` is `Sierra.`
    * <TEI>
    *   <teiHeader>
    *     <fileDesc>
    *       <sourceDesc>
    *         <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *           <msIdentifier>
    *               <altIdentifier type="former">
    *                 <idno>WMS. Or. 1a (Iskandar)</idno>
    *               </altIdentifier>
    *               <altIdentifier type="former">
    *                 <idno>WMS. Or. 1a</idno>
    *                </altIdentifier>
    *               <altIdentifier type="Sierra">
    *                 <idno>b1234567</idno>
    *                </altIdentifier>
    *           </msIdentifier>
    *      ...
    * </TEI>
    *
    */
  def bNumber: Result[Option[String]] = {
    val identifiersNodes = xml \\ "msDesc" \ "msIdentifier" \ "altIdentifier"
    val seq = (identifiersNodes.filter(
      n => (n \@ "type").toLowerCase == "sierra"
    ) \ "idno").toList
    seq match {
      case List(node) => Right(Some(node.text.trim))
      case Nil        => Right(None)
      case _          => Left(new RuntimeException("More than one sierra bnumber node!"))
    }
  }

  /**
    * The summary of the TEI is in the `summary` node under `msContents`. There is supposed to be only one summary node
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
    *    <teiHeader>
    *      <fileDesc>
    *        <sourceDesc>
    *          <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *            <msContents>
    *              <summary>1 copy of al-Qānūn fī al-ṭibb by Avicenna, 980-1037</summary>
    *    ...
    *    </TEI>
    *
    */
  def summary(nodeSeq: NodeSeq = (xml \\ "msDesc")): Result[Option[String]] = {
    (nodeSeq \ "msContents" \ "summary").toList match {
      case List(node) =>
        // some summary nodes can contain TEI specific xml tags, so we remove them
        Right(Some(node.text.trim.replaceAll("<.*?>", "")))
      case Nil => Right(None)
      case _   => Left(new RuntimeException("More than one summary node!"))
    }
  }

  /**
    * In an XML like this:
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651">
    *  <teiHeader>
    *    <fileDesc>
    *      <publicationStmt>
    *        <idno type="msID">Well. Jav. 4</idno>
    *       </publicationStmt>
    * Extract "Well. Jav. 4" as the title
    */
  def title: Result[String] = {
    val nodes =
      (xml \ "teiHeader" \ "fileDesc" \ "publicationStmt" \ "idno").toList
    val maybeTitles = nodes.filter(n => (n \@ "type") == "msID")
    maybeTitles match {
      case List(titleNode) => Right(titleNode.text)
      case Nil             => Left(new RuntimeException("No title found!"))
      case _               => Left(new RuntimeException("More than one title node!"))
    }
  }

  /**
    * TEI works can be composed of other works.
    * This function extracts the information about these nested works.
    *
    * Nested works can be specified in TEI as msItem or msPart depending
    * if the manuscript is a single part manuscript or a multipart manuscript.
    * check https://github.com/wellcomecollection/wellcome-collection-tei/blob/main/docs/TEI_Manual_2020_V1.pdf
    * for more info.
    */
  def nestedTeiData =
    for {
      wrapperTitle <- title
      catalogues <- getCatalogues
      nestedItems <- nestedTeiDataFromItems(
        wrapperTitle = wrapperTitle,
        catalogues = catalogues)
      nestedData <- nestedItems match {
        case Nil =>
          nestedTeiDataFromParts(
            wrapperTitle = wrapperTitle,
            catalogues = catalogues)
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
    wrapperTitle: String,
    catalogues: List[String]): Result[List[TeiData]] =
    (xml \\ "msDesc" \ "msPart").zipWithIndex
      .map { case (node, i) => (node, i + 1) }
      .map {
        case (node, i) =>
          for {
            id <- getIdFrom(node)
            description <- summary(node)
            languages <- TeiLanguages.parseLanguages(node \ "msContents")
            partTitle = s"$wrapperTitle part $i"
            items <- extractLowerLevelItems(
              partTitle,
              node \ "msContents",
              catalogues)
          } yield {
            TeiData(
              id = id,
              title = partTitle,
              languages = languages,
              description = description,
              nestedTeiData = items)
          }
      }
      .toList
      .sequence

  /**
    * Extract information about inner works for single part manuscripts.
    * For single part manuscripts, inner works are described in msItem elements.
    */
  private def nestedTeiDataFromItems(
    wrapperTitle: String,
    catalogues: List[String],
    nodeSeq: NodeSeq = xml \\ "msDesc" \ "msContents"): Result[List[TeiData]] =
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
            id <- getIdFrom(node)
            languages <- TeiLanguages.parseLanguages(node)
            items <- extractLowerLevelItems(title, node, catalogues)
          } yield
            TeiData(
              id = id,
              title = title,
              languages = languages,
              nestedTeiData = items)
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
    partTitle: String,
    nodes: NodeSeq,
    catalogues: List[String]): Either[Throwable, List[TeiData]] =
    catalogues match {
      case catalogues if containsFihrist(catalogues) =>
        Right(Nil)
      case _ =>
        nestedTeiDataFromItems(
          wrapperTitle = partTitle,
          catalogues = catalogues,
          nodeSeq = nodes)
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
  private def getCatalogues: Result[List[String]] = {
    val nodes =
      (xml \ "teiHeader" \ "fileDesc" \ "publicationStmt" \ "idno").toList
    val maybeCatalogues = nodes.filter(n => (n \@ "type") == "catalogue")
    maybeCatalogues match {
      case l @ _ :: _ => Right(l.map(_.text))
      case Nil        => Right(Nil)
    }
  }

  private def containsFihrist(catalogues: List[String]): Boolean =
    catalogues.exists(_.trim.toLowerCase == "fihrist")

  private def getIdFrom(node: Node): Result[String] =
    Try(node.attributes
      .collectFirst {
        case metadata if metadata.key == "id" => metadata.value.text.trim
      }
      .getOrElse(throw new RuntimeException("Could not find an id in node!"))).toEither

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

object TeiXml {
  def apply(id: String, xmlString: String): Result[TeiXml] =
    for {
      xml <- Try(XML.loadString(xmlString)).toEither
      teiXml = new TeiXml(xml)
      _ <- Try(
        require(
          teiXml.id == id,
          s"Supplied id $id did not match id in XML ${teiXml.id}"
        )
      ).toEither
    } yield teiXml
}
