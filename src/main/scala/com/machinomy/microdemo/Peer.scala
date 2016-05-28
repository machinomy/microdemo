package com.machinomy.microdemo

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import com.github.nscala_time.time.Imports._
import com.machinomy.xicity._


object Peer {

  sealed trait PeerEvent
  case class ConnectedEvent() extends PeerEvent
  case class ReceivedEvent(from: Identifier, to: Identifier, message: Array[Byte], expiration: Long) extends PeerEvent

  import akka.actor.ActorSystem
  import com.machinomy.xicity.{Connector, Identifier, PeerNode}
  lazy val system = ActorSystem()

  val identifier = new Identifier(100)

  var logic: ActorRef = null

  type PeerCallback = (PeerEvent) => Unit

  private class Logic(handler: PeerCallback) extends Actor with ActorLogging {
    var peerNodeRef: ActorRef = null

    override def receive: Receive = {
      case PeerNode.DidStart(n) =>
        peerNodeRef = n
        log info "DID START"
        handler(ConnectedEvent())
      case msg @ PeerNode.ReceivedSingleMessage(from, to, text, expiration) =>
        log debug msg.toString
        handler(ReceivedEvent(msg.from, msg.to, msg.text, msg.expiration))
      case msg @ Logic.SendMessage(to, message) =>
        peerNodeRef ! PeerNode.SendSingleMessageCommand(identifier, to, message, 1.minute.seconds)
      case e => log.error(e.toString)
    }
  }

  private object Logic {

    case class SendMessage(to: Identifier, message: Array[Byte])

    def props(rcv: PeerCallback) = Props(classOf[Logic], rcv)
  }


  def start(rcv: PeerCallback) = {
    logic = system.actorOf(Logic.props(rcv))
    val peerNode = system.actorOf(PeerNode.props(identifier, logic))
    val seeds = Set(Connector("45.55.122.116"))

    peerNode ! PeerNode.StartServerCommand(Connector("0.0.0.0"))
    peerNode ! PeerNode.StartClientsCommand(2, seeds)
  }

//  def startServer(rcv: PeerCallback) = {
//    logic = system.actorOf(Logic.props(rcv))
//    val peerNode = system.actorOf(PeerNode.props(identifier, logic))
//    val seeds = Set(Connector("45.55.122.116"))
//
//        peerNode ! PeerNode.StartServerCommand(Connector("0.0.0.0"))
//    peerNode ! PeerNode.StartClientsCommand(2, seeds)
//  }


  def send(to: Identifier, message: Array[Byte]) = {
    Option[ActorRef](logic) foreach { _ ! Logic.SendMessage(to, message) }
  }

//  type PeerHandler = (Identifier, Identifier, Array[Byte], Long) => Unit
//
//  import com.machinomy.xicity.{Connector, Identifier, PeerNode}
//
//  private[this] lazy val identifier = new Identifier(34)
//
//  private[this] var handler: PeerHandler = null
//
//  private[this] lazy val peerNode = {
//    import akka.actor.ActorSystem
//
//    val system = ActorSystem()
//    system.actorOf(PeerNode.props(identifier, handler))
//  }
//
//  def send(to: Identifier, message: Array[Byte]) = {
//    val cmd = PeerNode.SendSingleMessageCommand(identifier, to, message, DateTime.now.getMillis / 1000 + 5)
//    peerNode ! cmd
//  }
//
//  def start(rcv: PeerHandler) = {
//    handler = rcv
//    val seeds = Set(Connector("45.55.122.116"))
//    peerNode ! PeerNode.StartClientsCommand(2, seeds)
//  }


}
