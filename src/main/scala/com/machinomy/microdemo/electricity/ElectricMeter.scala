package com.machinomy.microdemo.electricity

import akka.actor.{Props, ActorLogging, Actor}

import scala.concurrent.duration.FiniteDuration
import com.github.nscala_time.time.Imports._

class ElectricMeter extends Actor with ActorLogging {

  val quantum = 5.minutes
  val frequency = 500.millis

  override def receive: Actor.Receive = {
    case Messages.Start() =>
      log.info("Starting ElectricMeter")
      val system = context.system
      val originalSender = sender
      import system.dispatcher

      var currentTime = 0.seconds

      context.system.scheduler.schedule(FiniteDuration(0, "second"), FiniteDuration(frequency.millis, "ms")) {
        val metric = ElectricMetrics.random(currentTime.millis % 24.hours.millis)
        originalSender ! Messages.NewReadings(metric)
        currentTime += quantum
      }
  }
}

object ElectricMeter {
  def props() = Props(classOf[ElectricMeter])
}