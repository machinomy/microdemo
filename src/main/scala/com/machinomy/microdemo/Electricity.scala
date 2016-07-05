package com.machinomy.microdemo

import akka.actor.ActorSystem
import com.machinomy.microdemo.electricity.{Messages, ElectricMeter, House}

object Electricity extends App {

  val house = {
    val actorSystem = ActorSystem("microdemo-electricity")

    val electricMeter = actorSystem.actorOf(ElectricMeter.props())

    actorSystem.actorOf(House.props(electricMeter))
  }

  house ! Messages.Start()
}
