package com.machinomy.microdemo.communication

import akka.actor.Actor.Receive

import scala.collection.mutable
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.github.nscala_time.time.Imports._
import com.machinomy.xicity.Identifier
import com.machinomy.xicity.transport._
import sun.awt.PeerEvent

sealed trait PeerEvent
case class ConnectedEvent() extends PeerEvent
case class ReceivedEvent(from: Identifier, to: Identifier, message: Array[Byte], expiration: Long) extends PeerEvent

trait PeerActor extends Actor with ActorLogging {
  val identifier = Identifier(12)

  var nodeActor: ActorRef = null
  var clientNodeActor: ActorRef = null

  override def preStart(): Unit = {
    nodeActor = context.actorOf(Node.props(identifier, self))
    clientNodeActor = context.actorOf(ClientNode.props(Node.Wrap(nodeActor, Parameters.default)))
  }

  override def postStop(): Unit = {
    context.stop(nodeActor)
  }
}

object Peer {

  private[Peer] case class InternalShot(to: Identifier, protocol: Long, message: Array[Byte])

  type PeerCallback = PeerEvent => Unit

  private var actor: ActorRef = null

  private var listeners: Map[Long, mutable.Set[PeerCallback]] = Map.empty[Long, mutable.Set[PeerCallback]]

  def registerListenerForProtocol(protocol: Long, callback: PeerCallback): Unit = {
    if (!listeners.contains(protocol)) {
      listeners += protocol -> List.empty[PeerCallback]
    }

    listeners(protocol).add(callback)
  }

  def start(system: ActorSystem) = {
    actor = system.actorOf(Props(new PeerActor {
      override def receive: Receive = {
        case Node.IsReady() =>
          log.info("NODEISREADY")
          for {
            (_, callbacks) <- listeners
            callback <- callbacks
          } callback(ConnectedEvent())
        case m: Message.Shot =>
          log.info(s"RECEIVEDSHOT $m")
          for {
            (protocol, callbacks) <- listeners
            callback <- callbacks if protocol == m.protocol
          } callback(ReceivedEvent(m.from, m.to, m.text, m.expiration))

        case InternalShot(to, protocol, message) =>
          clientNodeActor ! Message.Shot(identifier, to, protocol, message, DateTime.now.getMillis / 1000 + 1.minute.seconds)
      }
    }))

  }

  def send(to: Identifier, protocol: Long, message: Array[Byte]) = {
    Option(actor) match {
      case Some(ref) => ref ! InternalShot(to, protocol, message)
      case None => // pass
    }
  }

}