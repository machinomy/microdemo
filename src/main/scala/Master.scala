import java.net.InetAddress
import java.util.Random

import net.tomp2p.connection.{Bindings, DefaultConnectionConfiguration, DiscoverNetworks}
import net.tomp2p.dht.PeerBuilderDHT
import net.tomp2p.p2p.PeerBuilder
import net.tomp2p.peers.Number160

import scala.collection.JavaConversions._

/**
  * Created by ukstv on 25/05/16.
  */
class Master {
  def main() = {
    val PORT = 9333

    val serverNumber = new Number160(new Random)
    val bindings = new Bindings().listenAny().addAddress(InetAddress.getByName("104.236.70.68"))
    val master = new PeerBuilderDHT(new PeerBuilder(serverNumber).ports(PORT).bindings(bindings).start()).start()
    //val master = new PeerBuilder(serverNumber).ports(PORT).bindings(bindings).start()
    println(s"Started listening to ${DiscoverNetworks.discoverInterfaces(bindings)}")
    println(s"outside address is ${master.peerAddress()}")
    while(true) {
      println("Another Iteration")
      for (peerAddress <- master.peerBean().peerMap().all()) {
        println(s"PeerAddress: $peerAddress")
        /*val fcc = master.peer().connectionBean().reservation().create(1, 1)
        fcc.awaitUninterruptibly()
        val channelCreator = fcc.channelCreator()

        val futureTcpResponse = master.peer().pingRPC().pingTCP(peerAddress, channelCreator, new DefaultConnectionConfiguration())
        futureTcpResponse.awaitUninterruptibly()

        if (futureTcpResponse.isSuccess) {
          println(s"Peer $peerAddress is online/tcp")
        } else {
          println(s"Peer $peerAddress is offline/tcp")
        }

        val futureUdpResponse = master.peer().pingRPC().pingUDP(peerAddress, channelCreator, new DefaultConnectionConfiguration())
        futureUdpResponse.awaitUninterruptibly()
        channelCreator.shutdown()

        if (futureUdpResponse.isSuccess) {
          println(s"Peer $peerAddress is online/udp")
        } else {
          println(s"Peer $peerAddress is offline/udp")
        }*/
      }
      Thread.sleep(1500)
    }
  }
}
