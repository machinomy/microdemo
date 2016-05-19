import im.tox.core.network.Port
import im.tox.tox4j._
import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data._
import im.tox.tox4j.core.enums._
import im.tox.tox4j.core.options.{SaveDataOptions, ToxOptions}
import one.eliot.microdemo.ToxUtil

class TestEventListener(id: Int) extends ToxCoreEventListener[Unit] {

  override def friendStatus(friendNumber: ToxFriendNumber, status: ToxUserStatus)(state: Unit): Unit = {
    println(s"[$id] friendStatus($friendNumber, $status)")
  }

  override def friendTyping(friendNumber: ToxFriendNumber, isTyping: Boolean)(state: Unit): Unit = {
    println(s"[$id] friendTyping($friendNumber, $isTyping)")
  }

  override def selfConnectionStatus(connectionStatus: ToxConnection)(state: Unit): Unit = {
    println(s"[$id] selfConnectionStatus($connectionStatus)")
  }

  override def friendName(friendNumber: ToxFriendNumber, name: ToxNickname)(state: Unit): Unit = {
    println(s"[$id] friendName($friendNumber, ${name.toString()})")
  }

  override def friendMessage(friendNumber: ToxFriendNumber, messageType: ToxMessageType, timeDelta: Int, message: ToxFriendMessage)(state: Unit): Unit = {
    println(s"[$id] friendMessage($friendNumber, $timeDelta, ${message.toString()})")
  }

  override def friendLossyPacket(friendNumber: ToxFriendNumber, data: ToxLossyPacket)(state: Unit): Unit = {
    println(s"[$id] friendLossyPacket($friendNumber, ${data.toString})")
  }

  override def fileRecv(friendNumber: ToxFriendNumber, fileNumber: Int, kind: Int, fileSize: Long, filename: ToxFilename)(state: Unit): Unit = {
    println(s"[$id] fileRecv($friendNumber, $fileNumber, $kind, $fileSize, ${filename.toString}})")
  }

  override def friendRequest(publicKey: ToxPublicKey, timeDelta: Int, message: ToxFriendRequestMessage)(state: Unit): Unit = {
    println(s"[$id] friendRequest($publicKey, $timeDelta, ${message.toString})")
  }

  override def fileChunkRequest(friendNumber: ToxFriendNumber, fileNumber: Int, position: Long, length: Int)(state: Unit): Unit = {
    println(s"[$id] fileChunkRequest($friendNumber, $fileNumber, $position, $length)")
  }

  override def fileRecvChunk(friendNumber: ToxFriendNumber, fileNumber: Int, position: Long, data: Array[Byte])(state: Unit): Unit = {
    println(s"[$id] fileRecvChunk($friendNumber, $fileNumber, $position, ${new String(data)})")
  }

  override def friendLosslessPacket(friendNumber: ToxFriendNumber, data: ToxLosslessPacket)(state: Unit): Unit = {
    println(s"[$id] friendLosslessPacket($friendNumber, ${data.toString})")
  }

  override def friendConnectionStatus(friendNumber: ToxFriendNumber, connectionStatus: ToxConnection)(state: Unit): Unit = {
    println(s"[$id] friendConnectionStatus($friendNumber, $connectionStatus)")
  }

  override def fileRecvControl(friendNumber: ToxFriendNumber, fileNumber: Int, control: ToxFileControl)(state: Unit): Unit = {
    println(s"[$id] fileRecvControl($friendNumber, $fileNumber, $control)")
  }

  override def friendStatusMessage(friendNumber: ToxFriendNumber, message: ToxStatusMessage)(state: Unit): Unit = {
    println(s"[$id] friendStatusMessage($friendNumber, ${message.toString})")
  }

  override def friendReadReceipt(friendNumber: ToxFriendNumber, messageId: Int)(state: Unit): Unit = {
    println(s"[$id] friendReadReceipt($friendNumber, $messageId)")
  }

}

val secretKey = ToxUtil.fromHex("CF47FC2891F617E244B48E3004DABA6B3BD8B56AA20BA3E062207B5E5CB4895D")
val options = ToxOptions(
  saveData = SaveDataOptions.SecretKey(ToxSecretKey(secretKey))
)
val baseNode = ToxUtil.buildNode(options)
baseNode.setName(ToxNickname("BASE".getBytes))
baseNode.setStatusMessage(ToxStatusMessage("Status".getBytes))
println(baseNode.getAddress)
baseNode.bootstrap("144.76.60.215", Port(33445), ToxPublicKey(ToxUtil.fromHex("04119E835DF3E78BACF0F84235B300546AF8B936F035185E2A8E9E0A67C8924F")))

val toxNode = ToxUtil.buildNode()
val receiverKey = baseNode.getPublicKey

toxNode.bootstrap("144.76.60.215", Port(33445), ToxPublicKey(ToxUtil.fromHex("04119E835DF3E78BACF0F84235B300546AF8B936F035185E2A8E9E0A67C8924F")))
toxNode.addFriend(baseNode.getAddress, ToxFriendRequestMessage("Hello, world!".toArray.map(_.toByte)))
baseNode.addFriendNorequest(toxNode.getPublicKey)
val toxToBase = toxNode.friendByPublicKey(receiverKey)
toxNode.iterate(new TestEventListener(toxToBase.value))(Unit)

val baseToTox = baseNode.friendByPublicKey(toxNode.getPublicKey)
baseNode.iterate(new TestEventListener(baseToTox.value))(Unit)
baseNode.invokeSelfConnectionStatus(ToxConnection.UDP)
baseNode.getUdpPort
baseNode.invokeFriendConnectionStatus(baseToTox, ToxConnection.TCP)

toxNode.friendSendMessage(toxToBase, ToxMessageType.NORMAL, 3000, ToxFriendMessage("FOOBLAH".toArray.map(_.toByte)))
