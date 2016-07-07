package com.machinomy.microdemo

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import com.machinomy.microdemo.electricity.web.{ElectricityWebService, WebSocketMessage}
import com.machinomy.microdemo.electricity.{ElectricMeter, House, Messages}
import com.machinomy.xicity.Identifier

object Electricity extends App with ElectricityWebService {

  override implicit val system = ActorSystem("microdemo-electricity")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  val notifier = system.actorOf(Props(new Actor {
    override def receive: Receive = {
      case Messages.NewReadings(meters) =>
        webSocketManager.sendMessage(WebSocketMessage("", meters.toString))
    }
  }))

  val house = {

    val electricMeter = system.actorOf(ElectricMeter.props())

    val identifierNumber = {
      try {
        args(1).toLong
      } catch {
        case _: IndexOutOfBoundsException => args(0).toLong
      }
    }

    println(s"\nElectricity: ${Identifier(identifierNumber)}\n")

    system.actorOf(House.props(electricMeter, notifier, Identifier(identifierNumber)))
  }

  try {
    Http().bindAndHandle(route, "localhost", 8888)
  } catch {
    case _: Throwable => //pass
  }

  house ! Messages.Start()

  sys addShutdownHook {
    house ! Messages.ShutDown()
  }
}
