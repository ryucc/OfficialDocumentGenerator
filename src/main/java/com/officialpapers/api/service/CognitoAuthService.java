package com.officialpapers.api.service;

import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RevokeTokenRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public class CognitoAuthService implements AuthService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String clientId;

    @Inject
    public CognitoAuthService(
            CognitoIdentityProviderClient cognitoClient,
            @Named("cognitoUserPoolClientId") String clientId
    ) {
        this.cognitoClient = cognitoClient;
        this.clientId = clientId;
    }

    @Override
    public void signUp(String email, String password) {
        try {
            cognitoClient.signUp(SignUpRequest.builder()
                    .clientId(clientId)
                    .username(normalizeEmail(email))
                    .password(requireNonBlank(password, "password is required"))
                    .userAttributes(List.of(AttributeType.builder()
                            .name("email")
                            .value(normalizeEmail(email))
                            .build()))
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            throw mapException(exception, AuthOperation.SIGN_UP);
        }
    }

    @Override
    public void confirmSignUp(String email, String confirmationCode) {
        try {
            cognitoClient.confirmSignUp(ConfirmSignUpRequest.builder()
                    .clientId(clientId)
                    .username(normalizeEmail(email))
                    .confirmationCode(requireNonBlank(confirmationCode, "confirmationCode is required"))
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            throw mapException(exception, AuthOperation.CONFIRM_SIGN_UP);
        }
    }

    @Override
    public void resendConfirmation(String email) {
        try {
            cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                    .clientId(clientId)
                    .username(normalizeEmail(email))
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            if (shouldSuppressExistenceFailure(exception, true)) {
                return;
            }
            throw mapException(exception, AuthOperation.RESEND_CONFIRMATION);
        }
    }

    @Override
    public AuthTokens login(String email, String password) {
        try {
            AuthenticationResultType result = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                            .clientId(clientId)
                            .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                            .authParameters(Map.of(
                                    "USERNAME", normalizeEmail(email),
                                    "PASSWORD", requireNonBlank(password, "password is required")
                            ))
                            .build())
                    .authenticationResult();
            return tokensFrom(result, null);
        } catch (CognitoIdentityProviderException exception) {
            throw mapException(exception, AuthOperation.LOGIN);
        }
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        String sanitizedRefreshToken = requireNonBlank(refreshToken, "refreshToken is required");
        try {
            AuthenticationResultType result = cognitoClient.initiateAuth(InitiateAuthRequest.builder()
                            .clientId(clientId)
                            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                            .authParameters(Map.of("REFRESH_TOKEN", sanitizedRefreshToken))
                            .build())
                    .authenticationResult();
            return tokensFrom(result, sanitizedRefreshToken);
        } catch (CognitoIdentityProviderException exception) {
            throw mapException(exception, AuthOperation.REFRESH);
        }
    }

    @Override
    public void forgotPassword(String email) {
        try {
            cognitoClient.forgotPassword(ForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .username(normalizeEmail(email))
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            if (shouldSuppressExistenceFailure(exception, false)) {
                return;
            }
            throw mapException(exception, AuthOperation.FORGOT_PASSWORD);
        }
    }

    @Override
    public void confirmForgotPassword(String email, String confirmationCode, String newPassword) {
        try {
            cognitoClient.confirmForgotPassword(ConfirmForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .username(normalizeEmail(email))
                    .confirmationCode(requireNonBlank(confirmationCode, "confirmationCode is required"))
                    .password(requireNonBlank(newPassword, "newPassword is required"))
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            throw mapException(exception, AuthOperation.CONFIRM_FORGOT_PASSWORD);
        }
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        String sanitizedAccessToken = requireNonBlank(accessToken, "accessToken is required");
        String sanitizedRefreshToken = requireNonBlank(refreshToken, "refreshToken is required");

        try {
            cognitoClient.globalSignOut(GlobalSignOutRequest.builder()
                    .accessToken(sanitizedAccessToken)
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            if (!shouldSuppressLogoutFailure(exception)) {
                throw mapException(exception, AuthOperation.LOGOUT);
            }
        }

        try {
            cognitoClient.revokeToken(RevokeTokenRequest.builder()
                    .clientId(clientId)
                    .token(sanitizedRefreshToken)
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            if (!shouldSuppressLogoutFailure(exception)) {
                throw mapException(exception, AuthOperation.LOGOUT);
            }
        }
    }

    @Override
    public AuthenticatedUser getCurrentUser(String accessToken) {
        try {
            GetUserResponse response = cognitoClient.getUser(GetUserRequest.builder()
                    .accessToken(requireNonBlank(accessToken, "accessToken is required"))
                    .build());
            Map<String, String> attributes = response.userAttributes().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            AttributeType::name,
                            AttributeType::value,
                            (left, right) -> right
                    ));
            String userId = firstNonBlank(attributes.get("sub"), response.username());
            if (userId == null) {
                throw new UnauthorizedException("UNAUTHORIZED", "Authenticated user is invalid");
            }
            return new AuthenticatedUser(
                    userId,
                    attributes.get("email"),
                    Boolean.parseBoolean(attributes.getOrDefault("email_verified", "false"))
            );
        } catch (CognitoIdentityProviderException exception) {
            throw mapException(exception, AuthOperation.ME);
        }
    }

    private AuthTokens tokensFrom(AuthenticationResultType result, String refreshTokenFallback) {
        if (result == null || result.accessToken() == null || result.idToken() == null || result.expiresIn() == null) {
            throw new ServiceUnavailableException("COGNITO_UNAVAILABLE", "Authentication provider returned an incomplete response");
        }
        return new AuthTokens(
                result.accessToken(),
                result.idToken(),
                firstNonBlank(result.refreshToken(), refreshTokenFallback),
                firstNonBlank(result.tokenType(), "Bearer"),
                result.expiresIn()
        );
    }

    private RuntimeException mapException(CognitoIdentityProviderException exception, AuthOperation operation) {
        String code = errorCode(exception);
        return switch (code) {
            case "UsernameExistsException" ->
                    new ConflictException("USER_ALREADY_EXISTS", "User already exists");
            case "AliasExistsException" ->
                    new ConflictException("EMAIL_ALREADY_IN_USE", "Email is already in use");
            case "CodeMismatchException" ->
                    new BadRequestException("Invalid confirmation code");
            case "ExpiredCodeException" ->
                    new BadRequestException("Confirmation code has expired");
            case "InvalidPasswordException" ->
                    new BadRequestException("Password does not meet policy requirements");
            case "InvalidParameterException", "UnsupportedTokenTypeException" ->
                    mapInvalidParameter(exception, operation);
            case "NotAuthorizedException" ->
                    mapNotAuthorized(exception, operation);
            case "UserNotConfirmedException" ->
                    new UnauthorizedException("USER_NOT_CONFIRMED", "User account is not confirmed");
            case "TooManyRequestsException", "TooManyFailedAttemptsException", "LimitExceededException" ->
                    new TooManyRequestsException("TOO_MANY_REQUESTS", "Too many requests");
            case "CodeDeliveryFailureException", "InternalErrorException", "UnexpectedLambdaException",
                    "InvalidLambdaResponseException", "ResourceNotFoundException" ->
                    new ServiceUnavailableException("COGNITO_UNAVAILABLE", "Authentication provider is unavailable");
            default ->
                    new ServiceUnavailableException("COGNITO_UNAVAILABLE", "Authentication provider is unavailable");
        };
    }

    private RuntimeException mapInvalidParameter(CognitoIdentityProviderException exception, AuthOperation operation) {
        if ((operation == AuthOperation.CONFIRM_SIGN_UP || operation == AuthOperation.RESEND_CONFIRMATION)
                && containsIgnoreCase(exception.getMessage(), "confirmed")) {
            return new ConflictException("USER_ALREADY_CONFIRMED", "User is already confirmed");
        }
        return new BadRequestException(defaultInvalidParameterMessage(operation));
    }

    private RuntimeException mapNotAuthorized(CognitoIdentityProviderException exception, AuthOperation operation) {
        if (operation == AuthOperation.CONFIRM_SIGN_UP && containsIgnoreCase(exception.getMessage(), "confirmed")) {
            return new ConflictException("USER_ALREADY_CONFIRMED", "User is already confirmed");
        }
        return switch (operation) {
            case LOGIN -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
            case REFRESH -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
            case ME, LOGOUT -> new UnauthorizedException("UNAUTHORIZED", "Authentication failed");
            default -> new UnauthorizedException("UNAUTHORIZED", "Authentication failed");
        };
    }

    private boolean shouldSuppressExistenceFailure(CognitoIdentityProviderException exception, boolean includeConfirmedUsers) {
        String code = errorCode(exception);
        if ("UserNotFoundException".equals(code) || "NotAuthorizedException".equals(code)) {
            return true;
        }
        return includeConfirmedUsers
                && "InvalidParameterException".equals(code)
                && containsIgnoreCase(exception.getMessage(), "confirmed");
    }

    private boolean shouldSuppressLogoutFailure(CognitoIdentityProviderException exception) {
        String code = errorCode(exception);
        return "NotAuthorizedException".equals(code) || "UserNotFoundException".equals(code);
    }

    private String normalizeEmail(String email) {
        return requireNonBlank(email, "email is required").toLowerCase(Locale.ROOT);
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String errorCode(CognitoIdentityProviderException exception) {
        if (exception.awsErrorDetails() != null && exception.awsErrorDetails().errorCode() != null) {
            return exception.awsErrorDetails().errorCode();
        }
        return exception.getClass().getSimpleName();
    }

    private String defaultInvalidParameterMessage(AuthOperation operation) {
        return switch (operation) {
            case CONFIRM_SIGN_UP, CONFIRM_FORGOT_PASSWORD -> "Confirmation code is invalid";
            case REFRESH -> "refreshToken is invalid";
            default -> "Request parameters are invalid";
        };
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private enum AuthOperation {
        SIGN_UP,
        CONFIRM_SIGN_UP,
        RESEND_CONFIRMATION,
        LOGIN,
        REFRESH,
        FORGOT_PASSWORD,
        CONFIRM_FORGOT_PASSWORD,
        LOGOUT,
        ME
    }
}
