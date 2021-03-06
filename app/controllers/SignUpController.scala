package controllers

import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.AuthInfoService
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import forms.SignUpForm
import models.User
import models.services.UserService
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action

import scala.concurrent.Future

/**
 * The sign up controller.
 *
 * @param env The Silhouette environment.
 * @param userService The user service implementation.
 * @param authInfoService The auth info service implementation.
 * @param passwordHasher The password hasher implementation.
 */
class SignUpController @Inject() (
  implicit val env: Environment[User, JWTAuthenticator],
  val userService: UserService,
  val authInfoService: AuthInfoService,
  val passwordHasher: PasswordHasher) extends Silhouette[User, JWTAuthenticator] {

  /**
   * Registers a new user.
   *
   * @return The result to display.
   */
  def signUp = Action.async { implicit request =>
    SignUpForm.form.bindFromRequest.fold (
      form => Future.successful(BadRequest(form.errorsAsJson)),
      data => {
        val loginInfo = LoginInfo(CredentialsProvider.ID, data.email)
        val authInfo = passwordHasher.hash(data.password)
        val user = User(
          userID = UUID.randomUUID(),
          loginInfo = loginInfo,
          firstName = Some(data.firstName),
          lastName = Some(data.lastName),
          fullName = Some(data.firstName + " " + data.lastName),
          email = Some(data.email),
          avatarURL = None
        )
        for {
          user <- userService.save(user)
          authInfo <- authInfoService.save(loginInfo, authInfo)
          authenticator <- env.authenticatorService.create(loginInfo)
          result <- env.authenticatorService.init(authenticator, Future.successful(Ok))
        } yield {
          env.eventBus.publish(SignUpEvent(user, request, request2lang))
          env.eventBus.publish(LoginEvent(user, request, request2lang))
          result
        }
      }
    )
  }
}
