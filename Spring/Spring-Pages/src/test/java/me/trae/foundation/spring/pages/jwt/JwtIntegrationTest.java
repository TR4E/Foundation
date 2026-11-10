package me.trae.foundation.spring.pages.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.account.CommonAccountProvider;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.cache.CacheControlData;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    void aProtectedPageRedirectsAnonymousVisitorsAndRendersForTheAuthenticatedAccount() throws Exception {
        this.mockMvc.perform(get("/orders")).andExpect(status().isFound()).andExpect(redirectedUrl("/auth?redirect=%2Forders"));
        this.mockMvc.perform(get("/orders").cookie(this.login())).andExpect(status().isOk()).andExpect(content().string("orders"));
    }

    @Test
    void aProtectedAssetIsHiddenWithoutAuthorization() throws Exception {
        this.mockMvc.perform(get("/js/jwt-orders.js")).andExpect(status().isNotFound());
        this.mockMvc.perform(get("/js/jwt-orders.js").cookie(this.login())).andExpect(status().isOk());
    }

    private Cookie[] login() throws Exception {
        final MockHttpServletResponse mockHttpServletResponse = this.mockMvc.perform(post("/session/login").header("Origin", "http://localhost").header("X-XSRF-TOKEN", "test-csrf").cookie(new Cookie("XSRF-TOKEN", "test-csrf")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return JwtTestFixture.cookies(mockHttpServletResponse);
    }

    @Test
    void wildcardProtectedAssetsAreServedOnlyToAuthorizedAccountsAndNeverSharedCached() throws Exception {
        this.mockMvc.perform(get("/assets/admin/wildcard.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, CacheControlData.PRIVATE_NO_STORE.getValue()));
        this.mockMvc.perform(get("/assets/admin/wildcard.js").cookie(this.login()))
                .andExpect(status().isOk())
                .andExpect(content().string("window.orders = true;"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, CacheControlData.PRIVATE_NO_STORE.getValue()));
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

        @Bean
        public Page ordersPage() {
            return new OrdersPage();
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

        @PostMapping("/session/logout")
        public void logout(final HttpServletRequest httpServletRequest, final HttpServletResponse mockHttpServletResponse) {
            this.jwtSecurity.getAuthenticator().getAccountByRequest(httpServletRequest, mockHttpServletResponse).ifPresent(account -> this.jwtSecurity.getCookie().remove(httpServletRequest, mockHttpServletResponse, account));
        }

        @RateLimit(scope = RateLimitScope.ACCOUNT, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
        @GetMapping("/session/http-limited")
        public String limited() {
            return "orders";
        }

        @GetMapping("/session/account")
        public ResponseEntity<String> account(final HttpServletRequest httpServletRequest, final HttpServletResponse mockHttpServletResponse) {
            return this.jwtSecurity.getAuthenticator().getAccountByRequest(httpServletRequest, mockHttpServletResponse)
                    .map(account -> ResponseEntity.ok(account.getId().toString()))
                    .orElseGet(() -> ResponseEntity.status(401).build());
        }

    }

    static final class OrdersPage extends Page {

        private OrdersPage() {
            super("/orders", JwtTestFixture.TestRole.ADMIN);
        }

        @Override
        public Set<String> getAssetPaths() {
            return Set.of("/js/jwt-orders.js", "/assets/admin/**");
        }

        @Render
        @ResponseBody
        public String render() {
            return "orders";
        }
    }

}