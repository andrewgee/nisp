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
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DefaultDB, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nisp.models.enums.APITypes._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait CachingModel[A, B] {
  def response: B
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat = ReactiveMongoFormats.objectIdFormats
}

trait CachingService[A, B] {
  def findByNino(nino: Nino, apiType: APITypes)(implicit formats: Reads[A], ec: ExecutionContext): Future[Option[B]]
  def insertByNino(nino: Nino, apiType: APITypes, response: B)(implicit formats: OFormat[A], ec: ExecutionContext): Future[Boolean]
}

class CachingMongoService[A <: CachingModel[A, B], B](formats: Format[A], apply: (String, B, DateTime) => A)(implicit mongo: () => DefaultDB, m: Manifest[A], ec: ExecutionContext)
  extends ReactiveRepository[A, BSONObjectID]("responses", mongo, formats)
    with CachingService[A, B] {

  val fieldName = "createdAt"
  val createdIndexName = "npsResponseExpiry"
  val expireAfterSeconds = "expireAfterSeconds"


  val timeToLive = 1200
  Logger.info(s"NPS Cache TTL set to $timeToLive")
  createIndex(fieldName, createdIndexName, timeToLive)

  private def createIndex(field: String, indexName: String, ttl: Int)(implicit ec: ExecutionContext): Future[Boolean] = {
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

  private def cacheKey(nino: Nino, api: APITypes) = s"$nino-$api"


  override def findByNino(nino: Nino, apiType: APITypes)(implicit formats: Reads[A], ec: ExecutionContext): Future[Option[B]] = {
    val tryResult = Try {
      //metrics.cacheRead()
      collection.find(Json.obj("key" -> cacheKey(nino, apiType))).cursor[A](ReadPreference.primary).collect[List]()
    }

    tryResult match {
      case Success(success) => {
        success.map { results =>
          Logger.debug(s"[$apiType][findByNino] : { cacheKey : ${cacheKey(nino, apiType)}, result: $results }")
          val response = results.headOption.map(_.response)
          response match {
            case Some(summaryModel) =>
              Some(summaryModel)
            case None => None
          }
        }
      }
      case Failure(failure) => {
        Logger.debug(s"[$apiType][findByNino] : { cacheKey : ${cacheKey(nino, apiType)}, exception: ${failure.getMessage} }")
        Future.successful(None)
      }
    }
  }

  override def insertByNino(nino: Nino, apiType: APITypes, response: B)(implicit formats: OFormat[A], ec: ExecutionContext): Future[Boolean] = {
    collection.insert(apply(cacheKey(nino, apiType), response, DateTime.now(DateTimeZone.UTC))).map { result =>
      Logger.debug(s"[$apiType][insertByNino] : { cacheKey : ${cacheKey(nino, apiType)}, request: $response, result: ${result.ok}, errors: ${result.errmsg} }")
      result.ok
    }
  }
}