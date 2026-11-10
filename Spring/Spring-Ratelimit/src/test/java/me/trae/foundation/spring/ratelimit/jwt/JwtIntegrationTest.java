package me.trae.foundation.spring.ratelimit.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.account.CommonAccountProvider;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import me.trae.foundation.spring.security.jwt.JwtProperties;
import me.trae.foundation.spring.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"foundation.production=false", "foundation.security.jwt.issuer=integration-test"})
final class JwtIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginCookiesAuthenticateTheNextRequest() throws Exception {
        this.mockMvc.perform(get("/session/account")).andExpect(status().isUnauthorized());
        this.mockMvc.perform(get("/session/account").cookie(this.login())).andExpect(status().isOk());
    }

    @Test
    void accountBudgetsAreSharedAcrossDifferentIpAddresses() throws Exception {
        final Cookie[] cookies = this.login();
        final Cookie[] secondCookies = this.login();
        this.mockMvc.perform(get("/session/limited").cookie(cookies).header("X-Forwarded-For", "203.0.113.1")).andExpect(status().isOk());
        this.mockMvc.perform(get("/session/limited").cookie(secondCookies).header("X-Forwarded-For", "203.0.113.2")).andExpect(status().isOk());
        this.mockMvc.perform(get("/session/limited").cookie(cookies).header("X-Forwarded-For", "203.0.113.3")).andExpect(status().isTooManyRequests());
        this.mockMvc.perform(get("/session/limited").header("X-Forwarded-For", "203.0.113.3")).andExpect(status().isOk());
    }

    private Cookie[] login() throws Exception {
        final MockHttpServletResponse mockHttpServletResponse = this.mockMvc.perform(post("/session/login").header("Origin", "http://localhost").header("X-XSRF-TOKEN", "test-csrf").cookie(new Cookie("XSRF-TOKEN", "test-csrf")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return JwtTestFixture.cookies(mockHttpServletResponse);
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static final class TestConfiguration {

        @Bean
        public JwtTestFixture jwtTestFixture(final JwtProperties jwtProperties) {
            jwtProperties.setAccessTokenKeySeed(JwtTestFixture.seed(1));
            jwtProperties.setRefreshTokenKeySeed(JwtTestFixture.seed(2));

            return new JwtTestFixture(false, jwtProperties);
        }

        @Bean
        public CommonAccountProvider<JwtTestFixture.Account> commonAccountProvider(final JwtTestFixture jwtTestFixture) {
            return jwtTestFixture.commonAccountProvider;
        }

        @Bean
        public SecurityAccountProvider<JwtTestFixture.Account> securityAccountProvider(final JwtTestFixture jwtTestFixture) {
            return jwtTestFixture.securityAccountProvider;
        }

        @Bean
        public JwtSecurity<JwtTestFixture.Account> jwtSecurity(final JwtTestFixture jwtTestFixture) {
            return jwtTestFixture.jwtSecurity;
        }

        @Bean
        public SessionController sessionController(final JwtSecurity<JwtTestFixture.Account> jwtSecurity, final JwtTestFixture jwtTestFixture) {
            return new SessionController(jwtSecurity, jwtTestFixture);
        }

    }

    @AllArgsConstructor
    @RestController
    static final class SessionController {

        private final JwtSecurity<JwtTestFixture.Account> jwtSecurity;
        private final JwtTestFixture jwtTestFixture;

        @PostMapping("/session/login")
        public void login(final HttpServletResponse mockHttpServletResponse) {
            this.jwtSecurity.getCookie().apply(mockHttpServletResponse, this.jwtTestFixture.account);
        }

        @GetMapping("/session/account")
        public ResponseEntity<String> account(final HttpServletRequest httpServletRequest, final HttpServletResponse mockHttpServletResponse) {
            return this.jwtSecurity.getAuthenticator().getAccountByRequest(httpServletRequest, mockHttpServletResponse)
                    .map(account -> ResponseEntity.ok(account.getId().toString()))
                    .orElseGet(() -> ResponseEntity.status(401).build());
        }

        @RateLimit(scope = RateLimitScope.ACCOUNT, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
        @GetMapping("/session/limited")
        public String limited() {
            return "orders";
        }

    }

}