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

package uk.gov.hmrc.nisp.connectors

import play.Logger
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.nisp.config.wiring.WSHttp
import uk.gov.hmrc.nisp.metrics.Metrics
import uk.gov.hmrc.nisp.models.enums.APITypes
import uk.gov.hmrc.nisp.models.enums.APITypes.APITypes
import uk.gov.hmrc.nisp.models.nps._
import uk.gov.hmrc.nisp.utils.NISPConstants
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object NpsConnector extends NpsConnector with ServicesConfig {
  override val serviceUrl = baseUrl("nps-hod")
  override val serviceOriginatorIdKey = getConfString("nps-hod.originatoridkey", "")
  override val serviceOriginatorId = getConfString("nps-hod.originatoridvalue", "")
  override def http: HttpGet = WSHttp
  override def metrics: Metrics = Metrics
}

trait NpsConnector {
  def http: HttpGet
  def metrics: Metrics

  val serviceUrl: String
  val serviceOriginatorIdKey: String
  val serviceOriginatorId: String
  def requestHeaderCarrier(implicit hc: HeaderCarrier): HeaderCarrier = hc.withExtraHeaders(serviceOriginatorIdKey -> serviceOriginatorId)

  class JsonValidationException(message: String) extends Exception(message)

  def url(path: String): String = s"$serviceUrl$path"

  private def ninoWithoutSuffix(nino: Nino): String = nino.value.substring(0, NISPConstants.ninoLengthWithoutSuffix)

  def connectToSummary(nino: Nino)(implicit hc: HeaderCarrier): Future[NpsSummaryModel] = {

//    CachingService.findByNino(nino, APITypes.Summary) flatMap {
//      case Some(summary: NpsSummaryModel) => Future.successful(summary)
//      case _ =>
//        val urlToRead = url(s"/nps-rest-service/services/nps/pensions/${ninoWithoutSuffix(nino)}/sp_summary")
//        connectToNps[NpsSummaryModel](urlToRead, APITypes.Summary, requestHeaderCarrier)(hc, NpsSummaryModel.formats).map {
//          summary =>
//            CachingService.insertByNino(nino, APITypes.Summary, summary)
//            summary
//        }
//    }

    val urlToRead = url(s"/nps-rest-service/services/nps/pensions/${ninoWithoutSuffix(nino)}/sp_summary")
    connectToNps(urlToRead, APITypes.Summary, requestHeaderCarrier)(hc, NpsSummaryModel.formats)
  }

  def connectToNIRecord(nino: Nino)(implicit hc: HeaderCarrier): Future[NpsNIRecordModel] = {
    val urlToRead = url(s"/nps-rest-service/services/nps/pensions/${ninoWithoutSuffix(nino)}/ni_record")
    connectToNps(urlToRead, APITypes.NIRecord, requestHeaderCarrier)(hc, NpsNIRecordModel.formats)
  }

  def connectToLiabilities(nino: Nino)(implicit hc: HeaderCarrier): Future[List[NpsLiability]] = {
    val urlToRead = url(s"/nps-rest-service/services/nps/pensions/${ninoWithoutSuffix(nino)}/liabilities")
    connectToNps[NpsLiabilityContainer](urlToRead, APITypes.Liabilities, requestHeaderCarrier).map(_.npsLcdo004d)
  }

  def connectToSchemeMembership(nino: Nino)(implicit hc: HeaderCarrier): Future[List[NpsSchemeMembership]] = {
    val urlToRead = url(s"/nps-rest-service/services/nps/pensions/${ninoWithoutSuffix(nino)}/scheme")
    connectToNps[NpsSchemeMembershipContainer](urlToRead, APITypes.SchemeMembership, requestHeaderCarrier).map(_.npsLcdo022d)
  }

  private def connectToNps[A](url: String, api: APITypes, requestHc: HeaderCarrier)(implicit hc: HeaderCarrier, formats: Format[A]): Future[A] = {
    val timerContext = metrics.startTimer(api)
    val futureResponse = http.GET[HttpResponse](url)(hc = requestHc, rds = HttpReads.readRaw)

    futureResponse.map { httpResponse =>
      timerContext.stop()
      Try(httpResponse.json.validate[A]).flatMap( jsResult =>
        jsResult.fold(errs => Failure(new JsonValidationException(formatJsonErrors(errs))), valid => Success(valid))
      )
    } recover {
      // http-verbs throws exceptions, convert to Try
      case ex => Failure(ex)
    } flatMap (handleResult(api, url, _))
  }

  private def handleResult[A](api: APITypes, url: String, tryResult: Try[A]): Future[A] = {
    tryResult match {
      case Failure(ex) =>
        Logger.error(s"URL: $url (API: $api): ${ex.toString}", ex)
        metrics.incrementFailedCounter(api)
        Future.failed(ex)
      case Success(value) =>
        Future.successful(value)
    }
  }

  private def formatJsonErrors(errors: Seq[(JsPath, Seq[ValidationError])]): String = {
    errors.map(p => p._1 + " - " + p._2.map(_.message).mkString(",")).mkString(" | ")
  }
}
