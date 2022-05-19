package weco.pipeline.ingestor.common.models

import io.circe.syntax._
import io.circe.{Encoder, Json}
import weco.catalogue.display_model.Implicits._
import weco.catalogue.display_model.languages.DisplayLanguage
import weco.catalogue.display_model.locations.DisplayLicense
import weco.catalogue.display_model.work._
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.languages.MarcLanguageCodeList
import weco.catalogue.internal_model.work.{Availability, WorkData}

import java.time.{LocalDateTime, ZoneId}

/** We store aggregatable values in the Elasticsearch documents we store for the API.
  *
  * These values are serialised with the display models, so the API can read these values
  * and drop them directly into an API response, without needing to know about what the
  * display models look like.
  *
  * See https://github.com/wellcomecollection/docs/tree/main/rfcs/049-catalogue-api-aggregations-modelling
  *
  */
trait AggregatableValues {
  implicit class WorkDataOps(workData: WorkData[DataState.Identified]) {
    def genreAggregatableValues: List[String] =
      workData.genres
        .map(DisplayGenre(_, includesIdentifiers = false))
        .asJson(_.update("concepts", Json.fromValues(List())))

    def subjectAggregatableValues: List[String] =
      workData.subjects
        .map(DisplaySubject(_, includesIdentifiers = false))
        .asJson(_.update("concepts", Json.fromValues(List())))

    def contributorAggregatableValues: List[String] =
      workData.contributors
        .map(_.agent)
        .map(DisplayAbstractAgent(_, includesIdentifiers = false))
        .asJson(json => json.mapObject(_.remove("roles")))

    def licenseAggregatableValues: List[String] =
      workData.items
        .flatMap(_.locations)
        .flatMap(_.license)
        .map(DisplayLicense(_))
        .asJson()

    def languageAggregatableValues: List[String] =
      workData.languages
        .map(lang =>
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
        .map(DisplayLanguage(_))
        .asJson()

    def workTypeAggregatableValues: List[String] =
      workData.format.toList
        .map(DisplayFormat(_))
        .asJson()

    // Note: this is based on the previous Elasticsearch behaviour, which aggregated over
    // the start date of the periods.
    //
    // It's not obvious to me if aggregating by start date is the "best" behaviour,
    // or if it's the best we could squeeze into Elasticsearch terms aggregations.
    // I'm going to leave it as-is for now, but this is a note that we can revisit this
    // (and other aggregations) at some point, because this approach gives us more flexibility.
    def productionDateAggregatableValues: List[String] =
      workData.production
        .flatMap(_.dates)
        .flatMap(_.range)
        .map(range =>
          LocalDateTime.ofInstant(range.from, ZoneId.systemDefault()).getYear)
        .map(startYear => DisplayPeriod(label = startYear.toString))
        .asJson()
  }

  implicit class AvailabilityOps(availabilities: Set[Availability]) {
    def aggregatableValues: List[String] =
      availabilities.map(DisplayAvailability(_)).toList.asJson().sorted
  }

  implicit class JsonStringOps[T](t: List[T])(implicit encoder: Encoder[T]) {
    def asJson(transform: Json => Json = identity[Json]): List[String] =
      t.map(_.asJson.deepDropNullValues)
        .map(transform(_))
        .map(_.noSpaces)
  }

  implicit class JsonOps(json: Json) {
    // Update a key/value pair in a JSON object.
    //
    // e.g.
    //
    //    json =
    //    { "color": "red", "sides": 5 }
    //
    //    json.update("color", "blue")
    //    { "color": "blue", "sides": 5 }
    //
    // Note: this is meant to preserve the order of keys in the original object.
    //
    def update(key: String, value: Json): Json =
      json.mapObject(
        jsonObj =>
          Json
            .fromFields(
              jsonObj.toIterable
                .map {
                  case (k, v) =>
                    if (k == key) (key, value) else (k, v)
                }
            )
            .asObject
            .get)
  }
}
