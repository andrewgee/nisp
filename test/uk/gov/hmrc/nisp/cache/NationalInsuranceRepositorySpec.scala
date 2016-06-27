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

package uk.gov.hmrc.nisp.cache

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.nisp.helpers.TestAccountBuilder
import uk.gov.hmrc.nisp.models.enums.APITypes
import uk.gov.hmrc.nisp.models.nps._
import uk.gov.hmrc.nisp.services.CachingMongoService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits._

class NationalInsuranceRepositorySpec extends UnitSpec with OneServerPerSuite with MongoSpecSupport with MockitoSugar {

  val testNIModel = NpsNIRecordModel(
    TestAccountBuilder.regularNino.toString(),
    35,
    0,
    10,
    0,
    0,
    NpsDate(1975, 3, 4),
    List(
      NpsNITaxYear(2015, 1, 0, 0, None, None, None, None, None, None, None, None, None, Some(20000), None, None, None, None, None, None)
    )

  )

  "NationalInsuranceRepository" should {



    val service = new CachingMongoService[NationalInsuranceCacheModel, NpsNIRecordModel](NationalInsuranceCacheModel.formats, NationalInsuranceCacheModel.apply)

    "persist a NIRecordModel in the repo" in {

      val resultF = service.insertByNino(TestAccountBuilder.regularNino, APITypes.NIRecord, testNIModel)
      await(resultF) shouldBe true
    }

    "find a NIRecordModel in the repo" in {
      val resultF = service.findByNino(TestAccountBuilder.regularNino, APITypes.NIRecord)
      resultF.get shouldBe testNIModel
    }

    "return None when there is nothing in the repo" in {
      val resultF = service.findByNino(TestAccountBuilder.excludedNino, APITypes.NIRecord)
      await(resultF) shouldBe None
    }

    "return None when there is a Mongo error" in {

      val stubCollection = mock[JSONCollection]
      val stubIndexesManager = mock[CollectionIndexesManager]

      when(stubCollection.indexesManager).thenReturn(stubIndexesManager)

      class TestSummaryMongoService extends CachingMongoService[NationalInsuranceCacheModel, NpsNIRecordModel
        ](NationalInsuranceCacheModel.formats, NationalInsuranceCacheModel.apply)  {
        override lazy val collection = stubCollection
      }
      when(stubCollection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
      when(stubCollection.indexesManager.ensure(Matchers.any())).thenReturn(Future.successful(true))

      val testRepository = new TestSummaryMongoService

      val found = await(testRepository.findByNino(TestAccountBuilder.excludedNino, APITypes.NIRecord))
      found shouldBe None
    }

  }



}
