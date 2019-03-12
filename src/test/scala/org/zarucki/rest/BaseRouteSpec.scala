package org.zarucki.rest

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._

class BaseRouteSpec
    extends FlatSpec
    with ScalatestRouteTest
    with Assertions
    with OptionValues
    with Inspectors
    with Matchers
