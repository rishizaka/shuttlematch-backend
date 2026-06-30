package com.shuttlematch.application.usecase.user;

import com.shuttlematch.application.InvalidCredentialsException;
import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * メールアドレス + パスワードでログインするユースケース。
 * <p>
 * パスワード未設定(password_hash が null)の既存ユーザーは、デフォルトパスワード
 * {@value #DEFAULT_PASSWORD} でログインできる(Cognito 導入前の暫定措置)。
 */
@Service
public class LoginUseCase {

    /** パスワード未設定ユーザーのデフォルトパスワード。 */
    public static final String DEFAULT_PASSWORD = "abcd1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User execute(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "メールアドレスまたはパスワードが正しくありません"));

        boolean ok = user.hasPassword()
                ? passwordEncoder.matches(rawPassword, user.passwordHash())
                : DEFAULT_PASSWORD.equals(rawPassword);
        if (!ok) {
            throw new InvalidCredentialsException("メールアドレスまたはパスワードが正しくありません");
        }
        return user;
    }
}
