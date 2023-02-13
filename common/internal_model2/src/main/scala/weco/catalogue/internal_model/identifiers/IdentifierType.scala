package weco.catalogue.internal_model.identifiers

import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}

sealed trait IdentifierType extends EnumEntry {
  val id: String
  val label: String

  override lazy val entryName: String = id
}

object IdentifierType extends Enum[IdentifierType] {
  val values = findValues
  assert(
    values.size == values.map { _.id }.toSet.size,
    "IDs for IdentifierType are not unique!"
  )

  implicit val identifierTypeEncoder: Encoder[IdentifierType] =
    Encoder.forProduct1("id")(_.id)

  implicit val identifierTypeDecoder: Decoder[IdentifierType] =
    Decoder.forProduct1("id")(IdentifierType.withName)

  def apply(id: String): IdentifierType =
    IdentifierType.withName(id)

  case object Tei extends IdentifierType {
    val id = "tei-manuscript-id"
    val label = "Tei manuscript id"
  }

  case object MiroImageNumber extends IdentifierType {
    val id = "miro-image-number"
    val label = "Miro image number"
  }

  case object MiroLibraryReference extends IdentifierType {
    val id = "miro-library-reference"
    val label = "Miro library reference"
  }

  case object SierraSystemNumber extends IdentifierType {
    val id = "sierra-system-number"
    val label = "Sierra system number"
  }

  case object SierraIdentifier extends IdentifierType {
    val id = "sierra-identifier"
    val label = "Sierra identifier"
  }

  case object LCGraphicMaterials extends IdentifierType {
    val id = "lc-gmgpc"
    val label = "Library of Congress Thesaurus for Graphic Materials"
  }

  case object LCSubjects extends IdentifierType {
    val id = "lc-subjects"
    val label = "Library of Congress Subject Headings (LCSH)"
  }

  case object LCNames extends IdentifierType {
    val id = "lc-names"
    val label = "Library of Congress Name authority records"
  }

  case object MESH extends IdentifierType {
    val id = "nlm-mesh"
    val label = "Medical Subject Headings (MeSH) identifier"
  }

  case object CalmRefNo extends IdentifierType {
    val id = "calm-ref-no"
    val label = "Calm RefNo"
  }

  case object CalmAltRefNo extends IdentifierType {
    val id = "calm-altref-no"
    val label = "Calm AltRefNo"
  }

  case object CalmRecordIdentifier extends IdentifierType {
    val id = "calm-record-id"
    val label = "Calm RecordIdentifier"
  }

  case object ISBN extends IdentifierType {
    val id = "isbn"
    val label = "International Standard Book Number"
  }

  case object ISSN extends IdentifierType {
    val id = "issn"
    val label = "ISSN"
  }

  case object METS extends IdentifierType {
    val id = "mets"
    val label = "METS"
  }

  case object METSImage extends IdentifierType {
    val id = "mets-image"
    val label = "METS image"
  }

  case object WellcomeDigcode extends IdentifierType {
    val id = "wellcome-digcode"
    val label = "Wellcome digcode"
  }

  case object IconographicNumber extends IdentifierType {
    val id = "iconographic-number"
    val label = "Iconographic number"
  }

  case object VIAF extends IdentifierType {
    val id = "viaf"
    val label = "VIAF: The Virtual International Authority File"
  }

  case object Fihrist extends IdentifierType {
    val id = "fihrist"
    val label = "Fihrist Authority"
  }

  // http://estc.bl.uk/ / https://www.wikidata.org/wiki/Property:P3939
  case object ESTC extends IdentifierType {
    val id = "bl-estc-citation-no"
    val label = "British Library English Short Title Catalogue"
  }

  // This is not a "real" identifier scheme, it exists so that things that only have a name
  // in the source data can be treated as though they are properly identified.
  // The expectation is that when a corresponding identifier is discovered for them
  // they can be replaced.
  case object LabelDerived extends IdentifierType {
    val id = "label-derived"
    val label = "Identifier derived from the label of the referent"
  }
}
