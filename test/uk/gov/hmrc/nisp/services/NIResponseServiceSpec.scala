/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.nisp.services

import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.nisp.connectors.NpsConnector
import uk.gov.hmrc.nisp.helpers.{StubMetrics, StubNpsConnector, TestAccountBuilder}
import uk.gov.hmrc.nisp.metrics.Metrics
import uk.gov.hmrc.nisp.models.enums.Exclusion
import uk.gov.hmrc.nisp.models.nps.NpsDate
import uk.gov.hmrc.nisp.models.{ExclusionsModel, NIRecordTaxYear}
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec

class NIResponseServiceSpec  extends UnitSpec with MockitoSugar with BeforeAndAfter with OneAppPerSuite {

  val nino = TestAccountBuilder.regularNino
  val exclusionNino = TestAccountBuilder.excludedNino
  val nonexistentnino = TestAccountBuilder.nonExistentNino

  implicit val hc = HeaderCarrier()

  val testNIService = new NIResponseService {
    override val nps: NpsConnector = StubNpsConnector
    override val metrics: Metrics = StubMetrics
    override def now: LocalDate = new LocalDate()
  }

  "customer with NINO regular has date of entry of 01/04/1972" should {
    "returns NIResponse" in {
      val niResponse = testNIService.getNIResponse(nino)
      niResponse.niRecord.get.taxYears.head shouldBe
        NIRecordTaxYear(1975, qualifying = true, 109.08, 0, 0, 0, None, None, None, payable = false, underInvestigation = false)
      niResponse.niRecord.get.taxYears.last shouldBe
        NIRecordTaxYear(2013, qualifying = true, 0, 52, 0, 0, None, None, None, payable = false, underInvestigation = false)
      niResponse.niRecord.get.taxYears.size shouldBe 39
    }
  }

  "customer with non-existent NINO" should {
    "returns failed Future" in {
      val niResponse = testNIService.getNIResponse(nonexistentnino)
      ScalaFutures.whenReady(niResponse.failed) { ex =>
        ex shouldBe a[NotFoundException]
      }
    }
  }

  "customer with excluded NINO" in {
    val niResponse = testNIService.getNIResponse(exclusionNino)
    niResponse.niRecord shouldBe None
    niResponse.niSummary shouldBe None
    niResponse.niExclusions shouldBe Some(ExclusionsModel(List(Exclusion.MWRRE)))
  }

  "calc pre75 years" should {
    "return 3 when the number of conts in 157 and the date of entry is 04/10/1972 and their date of birth is 04/10/1956" in {
      testNIService.calcPre75QualifyingYears(157, NpsDate(1972, 10, 4), NpsDate(1956, 10, 4)) shouldBe Some(3)
    }
    "return 8 when the number of conts in 408 and the date of entry is 08/01/1968 and their date of birth is 08/01/1952" in {
      testNIService.calcPre75QualifyingYears(408, NpsDate(1968, 1, 8), NpsDate(1952, 1, 8)) shouldBe Some(8)
    }
    "return 2 when the number of conts in 157 and the date of entry is 06/04/1973 and their date of birth is 04/10/1956" in {
      testNIService.calcPre75QualifyingYears(157, NpsDate(1973, 4, 6), NpsDate(1956, 10, 4)) shouldBe Some(2)
    }
    "return 1 when the number of conts in 157 and the date of entry is 06/04/1973 and their date of birth is 06/04/1958" in {
      testNIService.calcPre75QualifyingYears(157, NpsDate(1973, 4, 6), NpsDate(1958, 4, 6)) shouldBe Some(1)
    }
    "return 3 when the number of conts in 157 and the date of entry is 06/04/1973 and their date of birth is 24/05/1996" in {
      testNIService.calcPre75QualifyingYears(157, NpsDate(1973, 4, 6), NpsDate(1996, 5, 24)) shouldBe None
    }
    "return 3 when the number of conts in 157 and the date of entry is 06/04/1976 and their date of birth is 06/04/1960" in {
      testNIService.calcPre75QualifyingYears(157, NpsDate(1976, 4, 6), NpsDate(1960, 4, 6)) shouldBe None
    }
    "return 3 when the number of conts in 157 and the date of entry is 06/04/2005 and their date of birth is 06/04/1958" in {
      testNIService.calcPre75QualifyingYears(157, NpsDate(2005, 4, 6), NpsDate(1958, 4, 6)) shouldBe None
    }
  }
}
