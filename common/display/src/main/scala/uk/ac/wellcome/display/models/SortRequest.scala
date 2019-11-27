package uk.ac.wellcome.display.models

sealed trait SortRequest

case object ProductionDateSortRequest extends SortRequest
