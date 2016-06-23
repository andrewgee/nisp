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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.{DefaultDB, ReadPreference}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nisp.models.enums.APITypes.APITypes
import uk.gov.hmrc.nisp.models.nps.NpsSummaryModel

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

case class CachedResponse(key: String,
                             response: NpsSummaryModel,
                             createdAt: DateTime = DateTime.now(DateTimeZone.UTC))

object CachedResponse {
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat = ReactiveMongoFormats.objectIdFormats
  implicit def formats = Json.format[CachedResponse]
}

trait CachingService {
  def findByNino(nino: Nino, apiType: APITypes): Future[Option[NpsSummaryModel]]
  def insertByNino(nino: Nino, apiType: APITypes, response: NpsSummaryModel): Future[Boolean]
}

class CachingMongoService()(implicit mongo: () => DefaultDB)
  extends ReactiveRepository[CachedResponse, BSONObjectID]("responses", mongo, CachedResponse.formats)
    with CachingService {

  val fieldName = "createdAt"
  val createdIndexName = "npsResponseExpiry"
  val expireAfterSeconds = "expireAfterSeconds"


  val timeToLive = 1200
  Logger.info(s"NPS Cache TTL set to $timeToLive")
  createIndex(fieldName, createdIndexName, timeToLive)

  private def createIndex(field: String, indexName: String, ttl: Int): Future[Boolean] = {
    collection.indexesManager.ensure(Index(Seq((field, IndexType.Ascending)), Some(indexName),
      options = BSONDocument(expireAfterSeconds -> ttl))) map {
      result => {
        // $COVERAGE-OFF$
        Logger.debug(s"set [$indexName] with value $ttl -> result : $result")
        // $COVERAGE-ON$
        result
      }
    } recover {
      // $COVERAGE-OFF$
      case e => Logger.error("Failed to set TTL index", e)
        false
      // $COVERAGE-ON$
    }
  }

  private def cacheKey(nino: Nino, apiTypes: APITypes) = s"${nino.toString()}-${apiTypes.toString}"

  override def findByNino(nino: Nino, apiType: APITypes): Future[Option[NpsSummaryModel]] = {
    val tryResult = Try {
      //metrics.cacheRead()
      collection.find(Json.obj("key" -> cacheKey(nino, apiType))).cursor[CachedResponse](ReadPreference.primary).collect[List]()
    }

    tryResult match {
      case Success(success) => {
        success.map { results =>
          Logger.debug(s"[CachingMongoService][findByNino] : { cacheKey : ${cacheKey(nino, apiType)}, result: $results }")
          val response = results.headOption.map(_.response)
          response match {
            case Some(summaryModel) =>
              Some(summaryModel)
            case None => None
          }
        }
      }
      case Failure(failure) => {
        Logger.debug(s"[CachingMongoService][findByNino] : { cacheKey : ${cacheKey(nino, apiType)}, exception: ${failure.getMessage} }")
        Future.successful(None)
      }
    }
  }

  override def insertByNino(nino: Nino, apiType: APITypes, response: NpsSummaryModel): Future[Boolean] = {
    collection.insert(CachedResponse(cacheKey(nino, apiType), response)).map { result =>
      Logger.debug(s"[CachingMongoService][insertByNino] : { cacheKey : ${cacheKey(nino, apiType)}, request: $response, result: ${result.ok}, errors: ${result.errmsg} }")
      result.ok
    }
  }
}

object CachingService extends MongoDbConnection {

  private lazy val cacheService = new CachingMongoService()

  def apply(): CachingMongoService = cacheService

}

