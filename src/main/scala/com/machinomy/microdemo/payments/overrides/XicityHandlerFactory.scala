package com.machinomy.microdemo.payments.overrides

trait XicityHandlerFactory {
  def onNewConnection (clientAddress: XicityAddress): XicityServerConnectionEventHandler
}
