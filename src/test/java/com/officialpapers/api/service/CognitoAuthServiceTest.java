package com.officialpapers.api.service;

import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        AuthTokens tokens = service.login("user@example.com", "secret-password");

        assertEquals("access-token", tokens.accessToken());

        ArgumentCaptor<InitiateAuthRequest> captor = ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(cognitoClient).initiateAuth(captor.capture());
        assertEquals("client-123", captor.getValue().clientId());
        assertEquals("user@example.com", captor.getValue().authParameters().get("USERNAME"));
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
    void signUpMapsExistingUserToConflict() {
        when(cognitoClient.signUp(any(SignUpRequest.class))).thenThrow(
                UsernameExistsException.builder().message("exists").build()
        );

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.signUp("user@example.com", "secret-password")
        );

        assertEquals("USER_ALREADY_EXISTS", exception.code());
    }

    @Test
    void resendConfirmationSuppressesMissingUser() {
        when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class))).thenThrow(
                NotAuthorizedException.builder().message("not found").build()
        );

        service.resendConfirmation("missing@example.com");

        verify(cognitoClient).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
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
    }
}
