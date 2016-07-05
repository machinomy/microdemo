package com.machinomy.microdemo.electricity.web

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

case class WebSocketMessage(sender: String, text: String)

trait ElectricityWebService extends Directives {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  lazy protected val webSocketManager = new WebSocketManager(system)

  def route: Route =
    path("") {
      get {
        getFromResource("electricity/web/index.html")
      }
    } ~
    path("ws") {
      get {
        parameter('name) {
          name =>
            handleWebSocketMessages(
              Flow[Message]
                .collect({
                  case TextMessage.Strict(text) => text
                })
                .via(webSocketManager.chatFlow(name))
                .map({
                  case WebSocketMessage(_, text) =>
                    TextMessage.Strict(text)
                })
            )
        }
      }
    }
}