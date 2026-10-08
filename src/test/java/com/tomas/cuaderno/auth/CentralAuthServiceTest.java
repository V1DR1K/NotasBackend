package com.tomas.cuaderno.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CentralAuthServiceTest {
    @Mock private CentralAuthUserRepository users;
    @Mock private CentralAuthRefreshTokenRepository refreshTokens;
    @Mock private CentralUserAppAccessRepository appAccess;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthProperties properties;

    private CentralAuthService auth;

    @BeforeEach
    void setUp() {
        auth = new CentralAuthService(users, refreshTokens, appAccess, passwordEncoder, jwtService, properties);
    }

    @Test
    void register_normalizesUsernameAndGrantsOnlyWhatPlanAccess() {
        when(users.existsByUsernameIgnoreCase("new.user")).thenReturn(false);
        when(passwordEncoder.encode("a-long-password")).thenReturn("{bcrypt}encoded");
        when(users.saveAndFlush(any(CentralAuthUser.class))).thenAnswer(call -> call.getArgument(0));
        when(appAccess.save(any(CentralUserAppAccess.class))).thenAnswer(call -> call.getArgument(0));
        when(jwtService.issueAccessToken(any(CentralAuthUser.class), org.mockito.ArgumentMatchers.eq("whatplan"),
                org.mockito.ArgumentMatchers.eq("USER"))).thenReturn("access-token");
        when(jwtService.expiresInSeconds()).thenReturn(900L);
        when(properties.getRefreshTokenDays()).thenReturn(30);

        var result = auth.register(" New.User ", "a-long-password");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.user().username()).isEqualTo("new.user");
        assertThat(result.user().role()).isEqualTo("USER");
        assertThat(result.user().status()).isEqualTo("ACTIVE");

        ArgumentCaptor<CentralAuthUser> user = ArgumentCaptor.forClass(CentralAuthUser.class);
        verify(users).saveAndFlush(user.capture());
        assertThat(user.getValue().getUsername()).isEqualTo("new.user");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("{bcrypt}encoded");
        assertThat(user.getValue().isMustChangePassword()).isFalse();

        ArgumentCaptor<CentralUserAppAccess> grant = ArgumentCaptor.forClass(CentralUserAppAccess.class);
        verify(appAccess).save(grant.capture());
        assertThat(grant.getValue().getAppCode()).isEqualTo("whatplan");
        assertThat(grant.getValue().getRole()).isEqualTo("USER");
        assertThat(grant.getValue().isEnabled()).isTrue();
    }

    @Test
    void register_duplicateUsernameReturnsGenericFailure() {
        when(users.existsByUsernameIgnoreCase("taken")).thenReturn(true);

        assertThatThrownBy(() -> auth.register("TAKEN", "a-long-password"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No se pudo completar el registro.");

        verifyNoInteractions(passwordEncoder, appAccess, refreshTokens, jwtService, properties);
    }

    @Test
    void register_uniqueConstraintRaceReturnsGenericFailure() {
        when(users.existsByUsernameIgnoreCase("new.user")).thenReturn(false);
        when(passwordEncoder.encode("a-long-password")).thenReturn("{bcrypt}encoded");
        when(users.saveAndFlush(any(CentralAuthUser.class)))
                .thenThrow(new DataIntegrityViolationException("username unique constraint"));

        assertThatThrownBy(() -> auth.register("new.user", "a-long-password"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No se pudo completar el registro.");

        verifyNoInteractions(appAccess, refreshTokens, jwtService, properties);
    }
}
