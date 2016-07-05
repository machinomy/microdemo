package com.machinomy.microdemo.electricity

object Messages {
  case class Start()
  case class NewReadings(meter: ElectricMetrics)
}
