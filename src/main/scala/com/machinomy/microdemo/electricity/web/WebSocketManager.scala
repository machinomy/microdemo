package com.machinomy.microdemo.electricity.web

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

class WebSocketManager(actorSystem: ActorSystem) {

  private val webSocketParticipants = actorSystem.actorOf(WebSocketParticipants.props(), "web-socket-participants")

  def webSocketInSink(sender: String) = Sink.actorRef[WebSocketEvent](webSocketParticipants, ParticipantLeft(sender))

  def webSocketFlow(sender: String): Flow[String, WebSocketMessage, Any] = {
    val in = Flow[String].map(IncomingMessage(sender, _)).to(webSocketInSink(sender))
    val out = Source.actorRef[WebSocketMessage](1, OverflowStrategy.fail).mapMaterializedValue(webSocketParticipants ! ParticipantJoined(sender, _))

    Flow.fromSinkAndSource(in, out)
  }

  def sendMessage(message: WebSocketMessage) = {
    webSocketParticipants ! message
  }
}
