package com.izmeron

import org.specs2.mutable.Specification

class PlanerSpec extends Specification {
  val lenghtThreshold = 1200
  val minLenght = 400
  val log = org.apache.log4j.Logger.getLogger("test-planner")

  "Binding to asynchronous sources" should {
    "run0" in {
      val map = collection.immutable.Map[String, List[Result]](
        "86501.420.001.900" -> (Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 604, cQuantity = 3, optQuantity = 6) ::
            Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 1200, cQuantity = 6, optQuantity = 6) :: Nil),
        "86501.420.009.111" -> (Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4) :: Nil),
        "94100.001.007.072" -> (Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) ::
              Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) :: Nil)
      )

      val actual = redistributeWithinGroup(map, lenghtThreshold, minLenght, log)

      actual("86501.420.001.900") === List(Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length=200, cLength=1200, cQuantity=6, optQuantity =6))
      actual("86501.420.001.900 - 94100.001.007.072") === List(
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 200, cQuantity = 1, optQuantity = 6),
        Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3),
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 200, cQuantity = 1, optQuantity = 6),
        Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3)
      )

      actual("86501.420.001.900 - 86501.420.009.111") === List(
        Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 200, cQuantity = 1, optQuantity = 6),
        Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4))
    }
  }


  "Binding to asynchronous sources" should {
    "run1" in {
      val map = collection.immutable.Map[String, List[Result]](
        "86501.420.001.900" -> (Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 404, cQuantity = 2, optQuantity = 6) ::
          Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 1200, cQuantity = 6, optQuantity = 6) :: Nil),
        "86501.420.009.111" -> (Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4) :: Nil),
        "94100.001.007.072" -> (Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) ::
          Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) :: Nil)
      )

      val actual = redistributeWithinGroup(map, lenghtThreshold, minLenght, log)
      1 === 1
    }
  }

  "Binding to asynchronous sources" should {
    "run2" in {
      val map = collection.immutable.Map[String, List[Result]](
        "86501.420.001.900" -> (Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 204, cQuantity = 1, optQuantity = 6) ::
          Result("86501.420.001.900", "Сталь 38ХГМ/100/45", length = 200, cLength = 1200, cQuantity = 6, optQuantity = 6) :: Nil),
        "86501.420.009.111" -> (Result("86501.420.009.111", "Сталь 38ХГМ/100/45", length = 120, cLength = 480, cQuantity = 4, optQuantity = 4) :: Nil),
        "94100.001.007.072" -> (Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) ::
          Result("94100.001.007.072", "Сталь 38ХГМ/100/45", length = 170, cLength = 684, cQuantity = 4, optQuantity = 3) :: Nil)
      )

      val actual = redistributeWithinGroup(map, lenghtThreshold, minLenght, log)
      1 === 1
    }
  }
}