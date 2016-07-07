package com.machinomy.microdemo.electricity

import com.machinomy.consensus.Production
import com.machinomy.xicity.Identifier

object Messages {
  sealed trait WebSocketHandled

  case class Start()

  case class NewReadings(meter: ElectricMetrics) extends WebSocketHandled
  case class ConsesusUpdates(table: Map[Identifier, Production]) extends WebSocketHandled
  case class PaymentHappened(to: Identifier, amount: Double) extends WebSocketHandled

  case class ShutDown()
}
