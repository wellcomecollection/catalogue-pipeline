package uk.ac.wellcome.models.work.internal

sealed trait Note {
  val content: String
}

case class GeneralNote(content: String) extends Note

case class BibliographicalInformation(content: String) extends Note

case class FundingInformation(content: String) extends Note

case class TimeAndPlaceNote(content: String) extends Note

case class CreditsNote(content: String) extends Note

case class ContentsNote(content: String) extends Note

case class DissertationNote(content: String) extends Note

case class CiteAsNote(content: String) extends Note

case class LocationOfOriginalNote(content: String) extends Note

case class BindingInformation(content: String) extends Note

case class BiographicalNote(content: String) extends Note

case class ReproductionNote(content: String) extends Note

case class TermsOfUse(content: String) extends Note

case class CopyrightNote(content: String) extends Note

case class PublicationsNote(content: String) extends Note

case class ExhibitionsNote(content: String) extends Note

case class AwardsNote(content: String) extends Note

case class OwnershipNote(content: String) extends Note

case class AcquisitionNote(content: String) extends Note

case class AppraisalNote(content: String) extends Note

case class AccrualsNote(content: String) extends Note

case class RelatedMaterial(content: String) extends Note

case class FindingAids(content: String) extends Note

case class ArrangementNote(content: String) extends Note
