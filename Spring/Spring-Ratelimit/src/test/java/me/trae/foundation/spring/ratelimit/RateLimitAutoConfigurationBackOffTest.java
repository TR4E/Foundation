package me.trae.foundation.spring.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.store.RateLimitStore;
import me.trae.foundation.spring.security.account.EmptySecurityAccountProvider;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
final class RateLimitAutoConfigurationBackOffTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RateLimitStore rateLimitStore;

    @Autowired
    private SecurityAccountProvider<?> accountProvider;

    @Test
    void anApplicationStoreReplacesTheDefault() {
        assertEquals(1, this.applicationContext.getBeansOfType(RateLimitStore.class).size());
        assertSame(TestConfiguration.STORE, this.rateLimitStore);
    }

    @Test
    void anApplicationAccountProviderReplacesTheDefault() {
        assertEquals(1, this.applicationContext.getBeansOfType(SecurityAccountProvider.class).size());
        assertSame(TestConfiguration.ACCOUNT_PROVIDER, this.accountProvider);
    }

    @Test
    void theApplicationStoreIsTheOneTheInterceptorCalls() throws Exception {
        final int before = TestConfiguration.STORE.callCount.get();

        this.mockMvc.perform(get("/account/limited").header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        assertEquals(before + 1, TestConfiguration.STORE.callCount.get());
    }

    @Test
    void accountScopeKeysByTheResolvedAccountRatherThanTheAddress() throws Exception {
        this.mockMvc.perform(get("/account/scoped").header("X-Forwarded-For", "203.0.113.2")).andExpect(status().isOk());
        this.mockMvc.perform(get("/account/scoped").header("X-Forwarded-For", "203.0.113.3")).andExpect(status().isOk());

        this.mockMvc.perform(get("/account/scoped").header("X-Forwarded-For", "203.0.113.4")).andExpect(status().isTooManyRequests());

        assertTrue(TestConfiguration.STORE.keyList.stream().anyMatch(key -> key.endsWith(":account:00000000-0000-0000-0000-000000000001")));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        static final CountingRateLimitStore STORE = new CountingRateLimitStore();
        static final SecurityAccountProvider<?> ACCOUNT_PROVIDER = new EmptySecurityAccountProvider() {

            @Override
            public Optional<TestAccount> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
                return Optional.of(new TestAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"), TestRole.MEMBER));
            }
        };

        @Bean
        public RateLimitStore rateLimitStore() {
            return STORE;
        }

        @Bean
        public SecurityAccountProvider<?> accountProvider() {
            return ACCOUNT_PROVIDER;
        }

        @Bean
        public AccountController accountController() {
            return new AccountController();
        }
    }

    static final class CountingRateLimitStore implements RateLimitStore {

        private final AtomicInteger callCount = new AtomicInteger();
        private final CopyOnWriteArrayList<String> keyList = new CopyOnWriteArrayList<>();

        @Override
        public long tryConsume(final String key, final RateLimitData rateLimitData) {
            this.callCount.incrementAndGet();
            this.keyList.add(key);

            return this.keyList.stream().filter(key::equals).count() > rateLimitData.getAttempts() ? 1_000L : 0L;
        }
    }

    @RestController
    @RequestMapping("/account")
    static class AccountController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 100, duration = 1, unit = TimeUnit.MINUTES)
        @GetMapping("/limited")
        public ResponseEntity<Void> limited() {
            return ResponseEntity.ok().build();
        }

        @RateLimit(scope = RateLimitScope.ACCOUNT, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
        @GetMapping("/scoped")
        public ResponseEntity<Void> scoped() {
            return ResponseEntity.ok().build();
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    @Getter
    static final class TestAccount implements AccountIdentity<TestRole> {

        @EqualsAndHashCode.Include
        private final UUID id;

        private final TestRole role;
    }

    enum TestRole implements AccountRoleDefinition {

        MEMBER
    }
}