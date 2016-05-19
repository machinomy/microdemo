package one.eliot.microdemo

import im.tox.tox4j.core.ToxCore
import im.tox.tox4j.core.exceptions.ToxNewException
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl

object ToxUtil {
  def buildNode(options: ToxOptions = ToxOptions()): ToxCore = {
    try {
      new ToxCoreImpl(options)
    } catch {
      case e: ToxNewException if e.code == ToxNewException.Code.PORT_ALLOC =>
        System.gc()
        new ToxCoreImpl(options)
    }
  }

  def fromHex(hex: String): Array[Byte] = {
    hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }
}
