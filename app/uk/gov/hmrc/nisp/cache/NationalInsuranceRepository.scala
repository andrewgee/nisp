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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nisp.models.nps.NpsNIRecordModel
import uk.gov.hmrc.nisp.services.{CachingModel, CachingMongoService}

import play.api.libs.concurrent.Execution.Implicits._

case class NationalInsuranceCacheModel(key: String,
                             response: NpsNIRecordModel,
                             createdAt: DateTime = DateTime.now(DateTimeZone.UTC)) extends CachingModel[NationalInsuranceCacheModel, NpsNIRecordModel]

object NationalInsuranceCacheModel {
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat = ReactiveMongoFormats.objectIdFormats
  implicit def formats = Json.format[NationalInsuranceCacheModel]
}

object NationalInsuranceRepository extends MongoDbConnection {

  private lazy val cacheService = new CachingMongoService[NationalInsuranceCacheModel, NpsNIRecordModel](NationalInsuranceCacheModel.formats, NationalInsuranceCacheModel.apply)

  def apply(): CachingMongoService[NationalInsuranceCacheModel, NpsNIRecordModel] = cacheService
}

