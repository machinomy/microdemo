package com.machinomy.microdemo.electricity.web

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.machinomy.microdemo.Electricity
import com.machinomy.xicity.Identifier

class WebSocketParticipants extends Actor with ActorLogging {

  var participants: Map[String, ActorRef] = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case ParticipantJoined(name, actor) =>
      participants += name -> actor
      actor ! WebSocketMessage("", Identifier(Electricity.identifierNumber).toString)
      log.debug(s"New WS user: $name")

    case ParticipantLeft(name) =>
      participants -= name
      log.debug(s"WS user $name has left")

    case msg: IncomingMessage =>
      log.info(s"received new message")

    case WebSocketMessage(_, text) =>
      broadcast(text)
  }

  def broadcast(msg: String): Unit = {
    participants.values.foreach(_ ! WebSocketMessage("", msg))
  }
}

object WebSocketParticipants {
  def props() = Props(classOf[WebSocketParticipants])
}