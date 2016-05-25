import com.machinomy.xicity.Peer
import net.tomp2p.peers.Number160

import scala.concurrent.ExecutionContext.Implicits.global

// checkPeerB.main(Array.empty)
object checkPeerB extends App {
  val maybePeer = Peer.build(new Number160(0xfb))
  maybePeer.onSuccess { case peer =>
    peer.reply { (sender, bytes) =>
      "WORD".getBytes
    }
  }
  maybePeer.onComplete { case y => println(y) }
}
