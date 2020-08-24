/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnectorImpl.SendEmailRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.http.AcceptLanguage
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EmailConnectorImpl])
trait EmailConnector {

  def sendSubscriptionConfirmationEmail(subscriptionDetails: SubscriptionDetails, cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def sendReturnSubmitConfirmationEmail(
    submitReturnResponse: SubmitReturnResponse,
    subscribedDetails: SubscribedDetails
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class EmailConnectorImpl @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) extends EmailConnector {

  val sendEmailUrl: String = s"${servicesConfig.baseUrl("email")}/hmrc/email"

  val accountCreatedTemplateId: String = servicesConfig.getString("email.account-created.template-id")

  val returnSubmittedTemplateId: String = servicesConfig.getString("email.return-submitted.template-id")

  override def sendSubscriptionConfirmationEmail(subscriptionDetails: SubscriptionDetails, cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    for {
      acceptLanguage <- EitherT.fromOption[Future](
                          AcceptLanguage.fromHeaderCarrier(hc),
                          Error("Could not find Accept-Language HTTP header")
                        )
      httpResponse   <- EitherT[Future, Error, HttpResponse](
                          http.POST[JsValue, HttpResponse](
                              sendEmailUrl,
                              Json.toJson(
                                SendEmailRequest(
                                  List(subscriptionDetails.emailAddress.value),
                                  EmailConnectorImpl.getEmailTemplate(acceptLanguage, accountCreatedTemplateId),
                                  Map(
                                    "name"         -> subscriptionDetails.contactName.value,
                                    "cgtReference" -> cgtReference.value
                                  ),
                                  force = false
                                )
                              )
                            )
                            .map(Right(_))
                            .recover {
                              case e => Left(Error(e))
                            }
                        )

    } yield httpResponse

  override def sendReturnSubmitConfirmationEmail(
    submitReturnResponse: SubmitReturnResponse,
    subscribedDetails: SubscribedDetails
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    for {
      acceptLanguage <- EitherT.fromOption[Future](
                          AcceptLanguage.fromHeaderCarrier(hc),
                          Error("Could not find Accept-Language HTTP header")
                        )
      httpResponse   <- EitherT[Future, Error, HttpResponse](
                          http.POST[JsValue, HttpResponse](
                              sendEmailUrl,
                              Json.toJson(
                                SendEmailRequest(
                                  List(subscribedDetails.emailAddress.value),
                                  EmailConnectorImpl.getEmailTemplate(acceptLanguage, returnSubmittedTemplateId),
                                  Map(
                                    "name"         -> subscribedDetails.contactName.value,
                                    "submissionId" -> submitReturnResponse.formBundleId
                                  ),
                                  force = false
                                )
                              )
                            )
                            .map(Right(_))
                            .recover {
                              case e => Left(Error(e))
                            }
                        )
    } yield httpResponse

}

object EmailConnectorImpl {

  final case class SendEmailRequest(
    to: List[String],
    templateId: String,
    parameters: Map[String, String],
    force: Boolean
  )

  implicit val sendEmailRequestWrites: Writes[SendEmailRequest] = Json.writes[SendEmailRequest]

  def getEmailTemplate(language: AcceptLanguage, baseTemplateName: String): String =
    language match {
      case AcceptLanguage.EN => baseTemplateName
      case AcceptLanguage.CY => baseTemplateName + "_" + AcceptLanguage.CY.toString.toLowerCase
    }

}
