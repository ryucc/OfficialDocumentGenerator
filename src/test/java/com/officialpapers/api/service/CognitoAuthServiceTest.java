package com.officialpapers.api.service;

import com.officialpapers.domain.AuthChallenge;
import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;
import com.officialpapers.domain.LoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RevokeTokenRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoAuthServiceTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    private CognitoAuthService service;

    @BeforeEach
    void setUp() {
        service = new CognitoAuthService(cognitoClient, "client-123");
    }

    @Test
    void loginReturnsTokensFromCognito() {
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(
                InitiateAuthResponse.builder()
                        .authenticationResult(AuthenticationResultType.builder()
                                .accessToken("access-token")
                                .idToken("id-token")
                                .refreshToken("refresh-token")
                                .tokenType("Bearer")
                                .expiresIn(3600)
                                .build())
                        .build()
        );

        LoginResult result = service.login("user@example.com", "secret-password");

        assertNotNull(result.tokens());
        assertEquals("access-token", result.tokens().accessToken());
        assertEquals(null, result.challenge());

        ArgumentCaptor<InitiateAuthRequest> captor = ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(cognitoClient).initiateAuth(captor.capture());
        assertEquals("client-123", captor.getValue().clientId());
        assertEquals("user@example.com", captor.getValue().authParameters().get("USERNAME"));
    }

    @Test
    void loginReturnsNewPasswordChallenge() {
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(
                InitiateAuthResponse.builder()
                        .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                        .session("challenge-session")
                        .build()
        );

        LoginResult result = service.login("User@example.com", "temp-password");

        assertEquals(null, result.tokens());
        AuthChallenge challenge = result.challenge();
        assertNotNull(challenge);
        assertEquals("NEW_PASSWORD_REQUIRED", challenge.challengeName());
        assertEquals("challenge-session", challenge.session());
        assertEquals("user@example.com", challenge.email());
    }

    @Test
    void loginMapsNotAuthorizedToUnauthorized() {
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class))).thenThrow(
                NotAuthorizedException.builder().message("bad credentials").build()
        );

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> service.login("user@example.com", "secret-password")
        );

        assertEquals("INVALID_CREDENTIALS", exception.code());
    }

    @Test
    void getCurrentUserReturnsMappedAttributes() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(
                GetUserResponse.builder()
                        .username("user-123")
                        .userAttributes(
                                AttributeType.builder().name("sub").value("user-123").build(),
                                AttributeType.builder().name("email").value("user@example.com").build(),
                                AttributeType.builder().name("email_verified").value("true").build()
                        )
                        .build()
        );

        AuthenticatedUser user = service.getCurrentUser("access-token");

        assertEquals("user-123", user.userId());
        assertEquals("user@example.com", user.email());
        assertEquals(true, user.emailVerified());
    }

    @Test
    void loginThrowsBadRequestWhenPasswordIsNull() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.login("user@example.com", null)
        );

        assertEquals("password is required", exception.getMessage());
    }

    @Test
    void refreshReturnsTokensWithFallbackRefreshToken() {
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(
                InitiateAuthResponse.builder()
                        .authenticationResult(AuthenticationResultType.builder()
                                .accessToken("new-access")
                                .idToken("new-id")
                                .tokenType("Bearer")
                                .expiresIn(3600)
                                .build())
                        .build()
        );

        AuthTokens tokens = service.refresh("refresh-token");

        assertEquals("new-access", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());
    }

    @Test
    void respondToNewPasswordReturnsTokens() {
        when(cognitoClient.respondToAuthChallenge(any(RespondToAuthChallengeRequest.class))).thenReturn(
                RespondToAuthChallengeResponse.builder()
                        .authenticationResult(AuthenticationResultType.builder()
                                .accessToken("access-token")
                                .idToken("id-token")
                                .refreshToken("refresh-token")
                                .tokenType("Bearer")
                                .expiresIn(3600)
                                .build())
                        .build()
        );

        AuthTokens tokens = service.respondToNewPassword("User@example.com", "new-password", "challenge-session");

        assertEquals("access-token", tokens.accessToken());

        ArgumentCaptor<RespondToAuthChallengeRequest> captor = ArgumentCaptor.forClass(RespondToAuthChallengeRequest.class);
        verify(cognitoClient).respondToAuthChallenge(captor.capture());
        assertEquals(ChallengeNameType.NEW_PASSWORD_REQUIRED, captor.getValue().challengeName());
        assertEquals("challenge-session", captor.getValue().session());
        assertEquals("user@example.com", captor.getValue().challengeResponses().get("USERNAME"));
        assertEquals("new-password", captor.getValue().challengeResponses().get("NEW_PASSWORD"));
    }

    @Test
    void respondToNewPasswordThrowsBadRequestWhenSessionIsBlank() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.respondToNewPassword("user@example.com", "new-password", " ")
        );

        assertEquals("session is required", exception.getMessage());
        verifyNoInteractions(cognitoClient);
    }

    @Test
    void refreshThrowsBadRequestWhenTokenIsBlank() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.refresh("   ")
        );

        assertEquals("refreshToken is required", exception.getMessage());
    }

    @Test
    void forgotPasswordSuppressesMissingUser() {
        when(cognitoClient.forgotPassword(any(ForgotPasswordRequest.class))).thenThrow(
                software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException.builder().build()
        );

        service.forgotPassword("missing@example.com");

        verify(cognitoClient).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPasswordThrowsBadRequestWhenCodeIsBlank() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.confirmForgotPassword("user@example.com", "", "new-password")
        );

        assertEquals("confirmationCode is required", exception.getMessage());
    }

    @Test
    void confirmForgotPasswordThrowsBadRequestWhenNewPasswordIsBlank() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.confirmForgotPassword("user@example.com", "123456", "")
        );

        assertEquals("newPassword is required", exception.getMessage());
    }

    @Test
    void logoutSuppressesNotAuthorizedErrors() {
        when(cognitoClient.globalSignOut(any(GlobalSignOutRequest.class))).thenThrow(
                NotAuthorizedException.builder().build()
        );

        service.logout("access-token", "refresh-token");

        verify(cognitoClient).globalSignOut(any(GlobalSignOutRequest.class));
        verify(cognitoClient).revokeToken(any(RevokeTokenRequest.class));
    }

    @Test
    void getCurrentUserThrowsUnauthorizedWhenSubIsMissing() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(
                GetUserResponse.builder()
                        .username("")
                        .userAttributes(
                                AttributeType.builder().name("email").value("user@example.com").build()
                        )
                        .build()
        );

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> service.getCurrentUser("access-token")
        );

        assertEquals("UNAUTHORIZED", exception.code());
    }

    @Test
    void loginMapsTooManyRequestsException() {
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class))).thenThrow(
                software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException.builder().build()
        );

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> service.login("user@example.com", "password")
        );

        assertEquals("TOO_MANY_REQUESTS", exception.code());
    }

}
