package com.machinomy.microdemo.electricity

import com.github.nscala_time.time.Imports._

import scala.util.Random

case class ElectricMetrics(generated: Double = 0, spent: Double = 0, time: Long = 0)

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

    //850 - (x - 200^0.5)^2 * 24

//    ((20736^2*200+4*20736+414765)^0.5+20736) / (2 * 20736)
    //(-(20736^2*200+4*20736+414765)^0.5-20736) / -(2 * 20736)


    val yExtrema = (0d, 840d)
    val xExtrema = (math.sqrt(200) - math.sqrt(35), math.sqrt(200) + math.sqrt(35))

    def withDeviation(x: Double): Double = {
      val deviation = x * 0.1
      val random = x + Random.nextDouble() * deviation * 2 - deviation
      math.max(random, yExtrema._1)
    }

    def kwh(x: Double) = {
      math.max(yExtrema._2 - math.pow(x - math.sqrt(200), 2) * 24, 0)
    }

    def spread(x: Long): Double = {
      (x.toDouble / 3600 / 1000) % 24
    }

    withDeviation(kwh(spread(time)))
  }

  def random(time: Long): ElectricMetrics = {
    ElectricMetrics(generated(time), spent(time), time)
  }
}
