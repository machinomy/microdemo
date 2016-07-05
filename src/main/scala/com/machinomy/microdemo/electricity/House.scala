package com.machinomy.microdemo.electricity

import akka.actor._

class House(meter: ActorRef, notifier: ActorRef) extends Actor with ActorLogging {

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    //TODO: start communication layer
  }

  override def receive: Receive = {

    case Messages.Start() =>
      log.info("Starting new House")
      meter ! Messages.Start()

    case msg @ Messages.NewReadings(metrics) =>
      log.info(s"New Readings: \n\tgenerated: ${metrics.generated.formatted("%.3f")} kWh\n\tspent: ${metrics.spent.formatted("%.3f")} kWh")
      notifier ! msg
  }
}


object House {

  def props(meter: ActorRef, notifier: ActorRef) = Props(classOf[House], meter, notifier)

}