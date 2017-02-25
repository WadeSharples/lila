package lila.game

import scala.concurrent.duration._

import org.specs2.mutable._
import org.specs2.specification._

import lila.db.ByteArray

class BinaryClockHistoryTest extends Specification {

  val eps = 40.millis

  "binary clock history" should {

    "handle empty vectors" in {
      BinaryFormat.clockHistory.writeSide(2.hours, 0.millis, Vector.empty) must_== ByteArray.empty
    }

    "handle singleton vectors" in {
      val times = Vector(12345.millis)
      val bytes = BinaryFormat.clockHistory.writeSide(123456.millis, 0.millis, times)
      val restored = BinaryFormat.clockHistory.readSide(123456.millis, 0.millis, bytes, 1)

      restored.size must_== 1
      (restored(0) - times(0)).abs should be_<=(eps)
    }

    "restorable" in {
      val times = Vector(
        0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45, 48, 51, 54, 57, 60, 63,
        66, 69, 72, 75, 78, 81, 84, 87, 90, 93, 96, 99, 102, 105, 108, 199, 333, 567, 666, 2000, 30
      ).map(t => (2100 - t) * 100.millis)
      val bytes = BinaryFormat.clockHistory.writeSide(2.hours, 2.minutes, times)
      val restored = BinaryFormat.clockHistory.readSide(2.hours, 2.minutes, bytes, times.size)
      times.size must_== restored.size
      (restored, times).zipped.map(_ - _).forall(_.abs <= eps) should beTrue
    }

    "restore correspondence" in {
      val times = Vector(1180, 2040, 800, 1910, 750, 2300, 480, 2580).map(t => 2.days - t.millis)
      val bytes = BinaryFormat.clockHistory.writeSide(2.days, 1.day, times)
      val restored = BinaryFormat.clockHistory.readSide(2.days, 1.day, bytes, times.size)
      times.size must_== restored.size
      (restored, times).zipped.map(_ - _).forall(_.abs <= eps) should beTrue
    }

    "not drift" in {
      val times = Vector(50090, 43210, 29990, 3210, 30440, 210, 20550, 770).map(_.millis)
      var restored = Vector.empty[FiniteDuration];
      val start = 60000.millis
      var end = 0.millis
      for (end <- times) {
        val binary = BinaryFormat.clockHistory.writeSide(start, end - 670.millis, restored :+ end)
        restored = BinaryFormat.clockHistory.readSide(start, end - 670.millis, binary, restored.size + 1)
      }
      times.size must_== restored.size
      (restored, times).zipped.map(_ - _).forall(_.abs <= eps) should beTrue
    }
  }
}
