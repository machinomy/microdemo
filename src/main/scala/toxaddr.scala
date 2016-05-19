import im.tox.tox4j.core.options._
import im.tox.tox4j.core._
import im.tox.tox4j.core.exceptions.ToxNewException
import im.tox.tox4j.impl.jni.{ToxCoreImpl, ToxLoadJniLibrary}


object Toxaddr extends App {
  val options = ToxOptions()
  ToxLoadJniLibrary.webFallbackEnabled = true
  println("Starting Tox Node")
  val node = make(options)
  println(node.getDhtId)

  def make(options: ToxOptions = ToxOptions()): ToxCore = {
    try {
      new ToxCoreImpl(options)
    } catch {
      case e: ToxNewException if e.code == ToxNewException.Code.PORT_ALLOC =>
        System.gc()
        new ToxCoreImpl(options)
    }
  }
}
