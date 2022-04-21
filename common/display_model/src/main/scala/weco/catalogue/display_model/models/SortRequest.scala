package weco.catalogue.display_model.models

sealed trait SortRequest

case object ProductionDateSortRequest extends SortRequest
