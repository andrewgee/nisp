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
import uk.gov.hmrc.nisp.models.nps.{NpsLiabilityContainer, NpsNIRecordModel}
import uk.gov.hmrc.nisp.services.{CachingModel, CachingMongoService}
import play.api.libs.concurrent.Execution.Implicits._
import uk.gov.hmrc.nisp.models.enums.APITypes
import uk.gov.hmrc.nisp.models.enums.APITypes.APITypes

case class LiabilitiesCacheModel(key: String,
                             response: NpsLiabilityContainer,
                             createdAt: DateTime = DateTime.now(DateTimeZone.UTC))
  extends CachingModel[LiabilitiesCacheModel, NpsLiabilityContainer] {
}

object LiabilitiesCacheModel {
  implicit def formats = Json.format[LiabilitiesCacheModel]
}

object LiabilitiesRepository extends MongoDbConnection {

  private lazy val cacheService = new CachingMongoService
    [LiabilitiesCacheModel, NpsLiabilityContainer](LiabilitiesCacheModel.formats, LiabilitiesCacheModel.apply, APITypes.Liabilities)

  def apply(): CachingMongoService[LiabilitiesCacheModel, NpsLiabilityContainer] = cacheService
}

