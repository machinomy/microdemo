package com.machinomy.microdemo.electricity

import com.github.nscala_time.time.Imports._

import scala.util.Random

case class ElectricMetrics(generated: Double = 0, spent: Double = 0)

object ElectricMetrics {

  private def spent(time: Long): Double = {

    val yExtrema = (250d, 800d)
    val xExtrema = (0d, math.sqrt(550))

    def withDeviation(x: Double): Double = {
      val deviation = x * 0.1
      val random = Random.nextDouble() * deviation * 2 - deviation
      x + random
    }

    def kwh(x: Double): Double = {
      yExtrema._2 - math.pow(x - xExtrema._2, 2)
    }

    def spread(x: Long): Double = {
      2 * xExtrema._2 / 24.hours.millis * x
    }

    withDeviation(kwh(spread(time)))
  }

  private def generated(time: Long): Double = {

    val yExtrema = (0d, 400d)
    val xExtrema = ((math.sqrt(1800) - math.sqrt(300)) / 3, (math.sqrt(1800) + math.sqrt(300)) / 3)

    def withDeviation(x: Double): Double = {
      val deviation = x * 0.1
      val random = x + Random.nextDouble() * deviation * 2 - deviation
      math.max(random, yExtrema._1)
    }

    def kwh(x: Double) = {
      math.max(yExtrema._2 - math.pow(x - math.sqrt(200), 2) * 12, 0)
    }

    def spread(x: Long) = {
      (xExtrema._2 + xExtrema._1) / 24.hours.millis * x
    }

    withDeviation(kwh(spread(time)))
  }

  def random(time: Long): ElectricMetrics = {
    ElectricMetrics(generated(time), spent(time))
  }
}
