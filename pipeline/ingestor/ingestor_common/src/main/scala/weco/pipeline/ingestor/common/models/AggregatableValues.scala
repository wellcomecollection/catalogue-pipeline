package weco.pipeline.ingestor.common.models

import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.languages.MarcLanguageCodeList
import weco.catalogue.internal_model.work.{Availability, WorkData}

import java.time.{LocalDate, ZoneOffset}

trait AggregatableValues {
  implicit class WorkDataOps(workData: WorkData[DataState.Identified]) {
    def genreAggregatableValues: List[AggregatableIdLabel] =
      workData.genres.map(
        genre =>
          AggregatableIdLabel.fromIdState(genre.concepts.headOption.map(_.id), genre.label)
      )

    def subjectAggregatableValues: List[AggregatableIdLabel] =
      workData.subjects.map(
        subject => AggregatableIdLabel.fromIdState(Some(subject.id), subject.label)
      )

    def contributorAggregatableValues: List[AggregatableIdLabel] =
      workData.contributors
        .map(_.agent)
        .map(agent => AggregatableIdLabel.fromIdState(Some(agent.id), agent.label))

    def licenseAggregatableValues: List[AggregatableIdLabel] =
      workData.items
        .flatMap(_.locations)
        .flatMap(_.license)
        .map(license => AggregatableIdLabel.fromId(Some(license.id), license.label))

    def languageAggregatableValues: List[AggregatableIdLabel] =
      workData.languages
        .map(
          lang =>
            // There are cases where two languages may have the same ID but different
            // labels, e.g. Chinese and Mandarin are two names for the same language
            // which has MARC language code "chi".  The distinct names may be important
            // for display on individual works pages, but for filtering/aggregating
            // we want to use the canonical labels.
            MarcLanguageCodeList.fromCode(lang.id) match {
              case Some(canonicalLang) => canonicalLang
              case None                => lang
            }
        )
        .distinct
        .map(language => AggregatableIdLabel.fromId(Some(language.id), language.label))

    def workTypeAggregatableValues: List[AggregatableIdLabel] =
      workData.format.toList
        .map(format => AggregatableIdLabel.fromId(Some(format.id), format.label))

    // Note: this is based on the previous Elasticsearch behaviour, which aggregated over
    // the start date of the periods.
    //
    // It's not obvious to me if aggregating by start date is the "best" behaviour,
    // or if it's the best we could squeeze into Elasticsearch terms aggregations.
    // I'm going to leave it as-is for now, but this is a note that we can revisit this
    // (and other aggregations) at some point, because this approach gives us more flexibility.
    def productionDateAggregatableValues: List[AggregatableIdLabel] =
      workData.production
        .flatMap(_.dates)
        .flatMap(_.range)
        .map(
          // Extract the year part from the start of the range.
          // range.from is an Instant with an underlying representation in Epoch Time.
          // Extracting the year using LocalDateTime requires it to be first localised
          // then the year in Local Time is extracted.
          // If the epoch time is close to either end of a year, then a non-zero timezone
          // offset could cause the "wrong" year to be returned.
          // This will be the case when range represents a whole year or range of years
          // where _.from is the very beginning of the year.
          range => LocalDate.ofInstant(range.from, ZoneOffset.UTC).getYear
        )
        .map(startYear => AggregatableIdLabel.fromId(None: Option[String], label = startYear.toString))
  }

  implicit class AvailabilityOps(availabilities: Set[Availability]) {
    def aggregatableValues: List[AggregatableIdLabel] =
      availabilities.map(
        availability =>
          AggregatableIdLabel.fromId(Some(availability.id), availability.label)
      ).toList
  }
}
