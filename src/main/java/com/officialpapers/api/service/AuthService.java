package com.officialpapers.api.service;

import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;

public interface AuthService {

    void signUp(String email, String password);

    void confirmSignUp(String email, String confirmationCode);

    void resendConfirmation(String email);

    AuthTokens login(String email, String password);

    AuthTokens refresh(String refreshToken);

    void forgotPassword(String email);

    void confirmForgotPassword(String email, String confirmationCode, String newPassword);

    void logout(String accessToken, String refreshToken);

    AuthenticatedUser getCurrentUser(String accessToken);
}
