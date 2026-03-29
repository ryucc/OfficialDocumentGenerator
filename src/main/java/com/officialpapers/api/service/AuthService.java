package com.officialpapers.api.service;

import com.officialpapers.domain.AuthTokens;
import com.officialpapers.domain.AuthenticatedUser;
import com.officialpapers.domain.LoginResult;

public interface AuthService {

    LoginResult login(String email, String password);

    AuthTokens refresh(String refreshToken);

    AuthTokens respondToNewPassword(String email, String newPassword, String session);

    void forgotPassword(String email);

    void confirmForgotPassword(String email, String confirmationCode, String newPassword);

    void logout(String accessToken, String refreshToken);

    AuthenticatedUser getCurrentUser(String accessToken);
}
