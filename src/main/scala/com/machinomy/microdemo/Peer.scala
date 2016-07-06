//package com.machinomy.microdemo
//
//import akka.actor.{Props, ActorRef, ActorLogging, Actor}
//import com.github.nscala_time.time.Imports._
//import com.machinomy.xicity._
//
//
//object Peer {
//
//  sealed trait PeerEvent
//  case class ConnectedEvent() extends PeerEvent
//  case class ReceivedEvent(from: Identifier, to: Identifier, message: Array[Byte], expiration: Long) extends PeerEvent
//
//  import akka.actor.ActorSystem
//  import com.machinomy.xicity.{Connector, Identifier, PeerNode}
//  lazy val system = ActorSystem()
//
//  var identifier: Identifier = null
//
//  var logic: ActorRef = null
//
//  type PeerCallback = (PeerEvent) => Unit
//
//  private class Logic(handler: PeerCallback) extends Actor with ActorLogging {
//    var peerNodeRef: ActorRef = null
//
//    override def receive: Receive = {
//      case PeerNode.DidStart(n) =>
//        peerNodeRef = n
//        log info "DID START"
//        handler(ConnectedEvent())
//      case msg @ PeerNode.ReceivedSingleMessage(from, to, text, expiration) =>
//        println(s"------------ received message from ${from.toString}")
//        handler(ReceivedEvent(msg.from, msg.to, msg.text, msg.expiration))
//      case Logic.SendMessage(to, message) =>
//        println(s"========== sending message to ${to.toString}")
//        peerNodeRef ! PeerNode.SendSingleMessageCommand(identifier, to, message, DateTime.now.getMillis / 1000 + 1.minute.seconds)
//      case e =>
//        log.info(e.toString)
//        log.info("++++++++++++++++++++++")
//    }
//  }
//
//  private object Logic {
//
//    case class SendMessage(to: Identifier, message: Array[Byte])
//
//    def props(rcv: PeerCallback) = Props(classOf[Logic], rcv)
//  }
//
//
//  def start(rcv: PeerCallback, myself: Connector = Connector("0.0.0.0"), seeds: Set[Connector] = Set(Connector("45.55.122.116"))) = {
//    logic = system.actorOf(Logic.props(rcv))
//    val peerNode = system.actorOf(PeerNode.props(identifier, logic))
//
//    peerNode ! PeerNode.StartServerCommand(myself)
//    peerNode ! PeerNode.StartClientsCommand(2, seeds)
//  }
//
//  def send(to: Identifier, message: Array[Byte]) = {
//    println(logic)
//    Option[ActorRef](logic) foreach { x: ActorRef =>
//      println("Peer.send: ", x)
//      x ! Logic.SendMessage(to, message)
//    }
//  }
//}
