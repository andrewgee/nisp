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

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global


class SummaryRepositorySpec extends UnitSpec with OneServerPerSuite with MongoSpecSupport with MockitoSugar {

  val testSummaryModel = NpsSummaryModel(
    TestAccountBuilder.regularNino.toString(),
    None,
    1,
    NpsDate(1956, 4, 6),
    None,
    NpsDate(2020, 4, 5),
    30,
    2019,
    Some(1),
    Some(35),
    None,
    None,
    None,
    NpsDate(2015, 4, 5),
    None,
    0,
    "F",
    0,
    NpsStatePensionAmount(
      Some(155.65),
      None,
      Some(155.65),
      None,
      NpsAmountA2016(
        119.30,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        0
      ),
      NpsAmountB2016(
        Some(155.65),
        None
      )
    ),
    NpsPensionForecast(
      155.65,
      40,
      155.65,
      155.65
    )
  )

  "SummaryMongoService" should {



    val service = new CachingMongoService[SummaryCacheModel, NpsSummaryModel](SummaryCacheModel.formats, SummaryCacheModel.apply)

    "persist a SummaryModel in the repo" in {

      val resultF = service.insertByNino(TestAccountBuilder.regularNino, APITypes.Summary, testSummaryModel)
      await(resultF) shouldBe true
    }

    "find a SummaryModel in the repo" in {
      val resultF = service.findByNino(TestAccountBuilder.regularNino, APITypes.Summary)
      resultF.get shouldBe testSummaryModel
    }

    "return None when there is nothing in the repo" in {
      val resultF = service.findByNino(TestAccountBuilder.excludedNino, APITypes.Summary)
      await(resultF) shouldBe None
    }

    "return None when there is a Mongo error" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      val stubCollection = mock[JSONCollection]
      val stubIndexesManager = mock[CollectionIndexesManager]

      when(stubCollection.indexesManager).thenReturn(stubIndexesManager)

      class TestSummaryMongoService extends CachingMongoService[SummaryCacheModel, NpsSummaryModel](SummaryCacheModel.formats, SummaryCacheModel.apply)  {
        override lazy val collection = stubCollection
      }
      when(stubCollection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
      when(stubCollection.indexesManager.ensure(Matchers.any())).thenReturn(Future.successful(true))

      val testRepository = new TestSummaryMongoService

      val found = await(testRepository.findByNino(TestAccountBuilder.excludedNino, APITypes.Summary))
      found shouldBe None
    }

  }



}
