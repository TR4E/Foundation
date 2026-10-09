package me.trae.foundation.spring.ratelimit;

import me.trae.foundation.spring.ratelimit.account.RateLimitAccountResolver;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.store.RateLimitStore;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private RateLimitAccountResolver rateLimitAccountResolver;

    @Test
    void anApplicationStoreReplacesTheDefault() {
        assertEquals(1, this.applicationContext.getBeansOfType(RateLimitStore.class).size());
        assertSame(TestConfiguration.STORE, this.rateLimitStore);
    }

    @Test
    void anApplicationAccountResolverReplacesTheDefault() {
        assertEquals(1, this.applicationContext.getBeansOfType(RateLimitAccountResolver.class).size());
        assertSame(TestConfiguration.ACCOUNT_RESOLVER, this.rateLimitAccountResolver);
    }

    @Test
    void theApplicationStoreIsTheOneTheInterceptorCalls() throws Exception {
        final int before = TestConfiguration.STORE.callCount.get();

        this.mockMvc.perform(get("/account/limited").header("X-Forwarded-For", "203.0.113.1")).andExpect(status().isOk());

        assertEquals(before + 1, TestConfiguration.STORE.callCount.get());
    }

    @Test
    void accountScopeKeysByTheResolvedAccountRatherThanTheAddress() throws Exception {
        this.mockMvc.perform(get("/account/scoped").header("X-Forwarded-For", "203.0.113.2")).andExpect(status().isOk());
        this.mockMvc.perform(get("/account/scoped").header("X-Forwarded-For", "203.0.113.3")).andExpect(status().isOk());

        this.mockMvc.perform(get("/account/scoped").header("X-Forwarded-For", "203.0.113.4")).andExpect(status().isTooManyRequests());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        static final CountingRateLimitStore STORE = new CountingRateLimitStore();
        static final RateLimitAccountResolver ACCOUNT_RESOLVER = httpServletRequest -> Optional.of("alice");

        @Bean
        public RateLimitStore rateLimitStore() {
            return STORE;
        }

        @Bean
        public RateLimitAccountResolver rateLimitAccountResolver() {
            return ACCOUNT_RESOLVER;
        }

        @Bean
        public AccountController accountController() {
            return new AccountController();
        }
    }

    static final class CountingRateLimitStore implements RateLimitStore {

        private final AtomicInteger callCount = new AtomicInteger();
        private final List<String> keyList = new CopyOnWriteArrayList<>();

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
}