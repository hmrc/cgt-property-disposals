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
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.i18n.{DefaultLangs, Lang, Langs, Messages, MessagesApi, MessagesImpl, MessagesProvider}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnectorImpl.SendEmailRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, LocalDateUtils}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.MoneyUtils
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EmailConnectorImpl])
trait EmailConnector {

  def sendSubscriptionConfirmationEmail(subscriptionDetails: SubscriptionDetails, cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def sendReturnSubmitConfirmationEmail(
    submitReturnResponse: SubmitReturnResponse,
    subscribedDetails: SubscribedDetails
  )(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class EmailConnectorImpl @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig,
  messagesApi: MessagesApi
)(
  implicit ec: ExecutionContext
) extends EmailConnector {

  val sendEmailUrl: String = s"${servicesConfig.baseUrl("email")}/hmrc/email"

  val accountCreatedTemplateId: String = servicesConfig.getString("email.account-created.template-id")

  val returnSubmittedTemplateId: String = servicesConfig.getString("email.return-submitted.template-id")

  val lang: Lang = Lang("en")

  implicit val messages: Messages = messagesApi.preferred(Seq(lang))

  override def sendSubscriptionConfirmationEmail(subscriptionDetails: SubscriptionDetails, cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .post(
          sendEmailUrl,
          Json.toJson(
            SendEmailRequest(
              List(subscriptionDetails.emailAddress.value),
              accountCreatedTemplateId,
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

  override def sendReturnSubmitConfirmationEmail(
    submitReturnResponse: SubmitReturnResponse,
    subscribedDetails: SubscribedDetails
  )(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val baseEmailParameters =
      Map("name" -> subscribedDetails.contactName.value, "submissionId" -> submitReturnResponse.formBundleId)

    val emailParameters = submitReturnResponse.charge.fold(
      baseEmailParameters + ("taxDue" -> MoneyUtils.formatAmountOfMoneyWithPoundSign(0))
    )(charge =>
      baseEmailParameters ++ Map(
        "taxDue"    -> MoneyUtils.formatAmountOfMoneyWithPoundSign(charge.amount.value),
        "chargeRef" -> charge.chargeReference,
        "dueDate"   -> LocalDateUtils.govDisplayFormat(charge.dueDate)
      )
    )

    EitherT[Future, Error, HttpResponse](
      http
        .post(
          sendEmailUrl,
          Json.toJson(
            SendEmailRequest(
              List(subscribedDetails.emailAddress.value),
              returnSubmittedTemplateId,
              emailParameters,
              force = false
            )
          )
        )
        .map(Right(_))
        .recover {
          case e => Left(Error(e))
        }
    )
  }
}

object EmailConnectorImpl {

  final case class SendEmailRequest(
    to: List[String],
    templateId: String,
    parameters: Map[String, String],
    force: Boolean
  )

  implicit val sendEmailRequestWrites: Writes[SendEmailRequest] = Json.writes[SendEmailRequest]

}
