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

class SchemeMembershipRepositorySpec extends UnitSpec with OneServerPerSuite with MongoSpecSupport with MockitoSugar {

  val testSchemeMembershipModel = NpsSchemeMembershipContainer(
    List(
      NpsSchemeMembership(Some(NpsDate(1980, 4, 6)), None),
      NpsSchemeMembership(Some(NpsDate(1975, 4, 6)), Some(NpsDate(1999, 12, 31)))
    )
  )

  "SchemeMembershipRepository" should {

    val service = new CachingMongoService[SchemeMembershipCacheModel, NpsSchemeMembershipContainer](SchemeMembershipCacheModel.formats, SchemeMembershipCacheModel.apply, APITypes.SchemeMembership)

    "persist a SchemeMembershipModel in the repo" in {

      val resultF = service.insertByNino(TestAccountBuilder.regularNino, testSchemeMembershipModel)
      await(resultF) shouldBe true
    }

    "find a SchemeMembershipModel in the repo" in {
      val resultF = service.findByNino(TestAccountBuilder.regularNino)
      resultF.get shouldBe testSchemeMembershipModel
    }

    "return None when there is nothing in the repo" in {
      val resultF = service.findByNino(TestAccountBuilder.excludedNino)
      await(resultF) shouldBe None
    }

    "return None when there is a Mongo error" in {

      val stubCollection = mock[JSONCollection]
      val stubIndexesManager = mock[CollectionIndexesManager]

      when(stubCollection.indexesManager).thenReturn(stubIndexesManager)

      class TestSummaryMongoService extends CachingMongoService[SchemeMembershipCacheModel, NpsSchemeMembershipContainer
        ](SchemeMembershipCacheModel.formats, SchemeMembershipCacheModel.apply, APITypes.SchemeMembership)  {
        override lazy val collection = stubCollection
      }
      when(stubCollection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
      when(stubCollection.indexesManager.ensure(Matchers.any())).thenReturn(Future.successful(true))

      val testRepository = new TestSummaryMongoService

      val found = await(testRepository.findByNino(TestAccountBuilder.excludedNino))
      found shouldBe None
    }

  }



}
