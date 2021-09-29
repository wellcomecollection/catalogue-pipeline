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
  def summary(nodeSeq: NodeSeq = (xml \\ "msDesc" \ "msContents" \ "summary"))
    : Result[Option[String]] = {
    nodeSeq.toList match {
      case List(node) =>
        // some summary nodes can contain TEI specific xml tags, so we remove them
        Right(Some(node.text.trim.replaceAll("<.*?>", "")))
      case Nil => Right(None)
      case _   => Left(new RuntimeException("More than one summary node!"))
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
  def nestedTeiData = nestedTeiDataFromItems.flatMap {
    case Nil      => title.flatMap(nestedTeiDataFromParts)
    case teiDatas => Right(teiDatas)
  }

  /**
    * Extract information about inner works for multi part manuscripts.
    * Multi part manuscripts have msPart elements containing information about inner works.
    * msParts don't have a title so we construct the title concatenating the
    * title of the wrapper work and the part number.
    */
  private def nestedTeiDataFromParts(
    wrapperTitle: String): Result[List[TeiData]] =
    (xml \\ "msDesc" \ "msPart")
      .map { node =>
        for {
          id <- getIdFrom(node)
          partNumber <- Try((node \@ "n").toInt).toEither
          description <- summary(node \ "summary")
          languages <- TeiLanguages.parseLanguages(node)
        } yield
          TeiData(
            id = id,
            title = s"$wrapperTitle part $partNumber",
            languages = languages,
            description = description)
      }
      .toList
      .sequence

  /**
    * Extract information about inner works for single part manuscripts.
    * For single part manuscripts, inner works are described in msItem elements.
    */
  private def nestedTeiDataFromItems: Result[List[TeiData]] =
    (xml \\ "msDesc" \ "msContents" \ "msItem")
      .map { node =>
        for {
          title <- getTitleFromItem(node)
          id <- getIdFrom(node)
          languages <- TeiLanguages.parseLanguages(node)
        } yield TeiData(id = id, title = title, languages = languages)
      }
      .toList
      .sequence

  private def getIdFrom(node: Node): Result[String] =
    Try(node.attributes
      .collectFirst {
        case metadata if metadata.key == "id" => metadata.value.text.trim
      }
      .getOrElse(throw new RuntimeException("Could not find an id in node!"))).toEither

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
  private def getTitleFromItem(itemNode: Node): Result[String] = {
    val titleNodes = (itemNode \ "title").toList
    titleNodes match {
      case List(titleNode) => Right(titleNode.text)
      case list =>
        list.filter(n => (n \@ "type").toLowerCase == "original") match {
          case List(singleNode) => Right(singleNode.text)
          case Nil =>
            Left(
              new RuntimeException(
                s"Cannot find original title in msItem $titleNodes"))
          case _ =>
            Left(
              new RuntimeException(
                s"Multiple titles with type original msItem $titleNodes"))
        }
    }
  }
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
