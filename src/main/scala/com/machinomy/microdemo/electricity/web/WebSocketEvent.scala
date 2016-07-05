package com.machinomy.microdemo.electricity.web

import akka.actor.ActorRef

sealed trait WebSocketEvent
case class ParticipantLeft(name: String) extends WebSocketEvent
case class ParticipantJoined(name: String, actor: ActorRef) extends WebSocketEvent
case class IncomingMessage(sender: String, message: String) extends WebSocketEvent
