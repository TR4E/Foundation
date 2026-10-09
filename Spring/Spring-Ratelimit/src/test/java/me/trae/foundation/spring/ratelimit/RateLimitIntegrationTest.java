package me.trae.foundation.spring.ratelimit;

import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimitShared;
import me.trae.foundation.spring.ratelimit.registry.RateLimitRegistry;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.scope.RateLimitTarget;
import me.trae.foundation.spring.ratelimit.store.RateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = "foundation.production=false")
final class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitRegistry rateLimitRegistry;

    @Autowired
    private RateLimitStore rateLimitStore;

    @Test
    void theRegistryAndStoreAreWiredIn() {
        assertNotNull(this.rateLimitRegistry);
        assertNotNull(this.rateLimitStore);
    }

    @Test
    void aMethodLimitRejectsTheThirdCall() throws Exception {
        this.mockMvc.perform(request("/method/limited", "203.0.113.2")).andExpect(status().isOk());
        this.mockMvc.perform(request("/method/limited", "203.0.113.2")).andExpect(status().isOk());

        final String body = this.mockMvc.perform(request("/method/limited", "203.0.113.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(body.contains("Too many requests."));
        assertTrue(body.contains("\"retryAfter\":"));
    }

    @Test
    void anUnannotatedMethodIsNeverLimited() throws Exception {
        for (int index = 0; index < 5; index++) {
            this.mockMvc.perform(request("/method/open", "203.0.113.3")).andExpect(status().isOk());
        }
    }

    @Test
    void aSharedTypeLimitIsSpentAcrossEveryEndpoint() throws Exception {
        this.mockMvc.perform(request("/shared-type/first", "203.0.113.4")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-type/second", "203.0.113.4")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-type/first", "203.0.113.4")).andExpect(status().isTooManyRequests());
    }

    @Test
    void aSharedMethodLimitIsSpentPerEndpoint() throws Exception {
        this.mockMvc.perform(request("/shared-method/first", "203.0.113.5")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-method/first", "203.0.113.5")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-method/first", "203.0.113.5")).andExpect(status().isTooManyRequests());

        this.mockMvc.perform(request("/shared-method/second", "203.0.113.5")).andExpect(status().isOk());
    }

    @Test
    void aMethodLimitReplacesTheSharedOne() throws Exception {
        this.mockMvc.perform(request("/shared-type/overridden", "203.0.113.6")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-type/overridden", "203.0.113.6")).andExpect(status().isTooManyRequests());

        this.mockMvc.perform(request("/shared-type/first", "203.0.113.6")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-type/second", "203.0.113.6")).andExpect(status().isOk());
        this.mockMvc.perform(request("/shared-type/first", "203.0.113.6")).andExpect(status().isTooManyRequests());
    }

    @Test
    void separateCallersHaveSeparateBudgets() throws Exception {
        this.mockMvc.perform(request("/method/limited", "203.0.113.7")).andExpect(status().isOk());
        this.mockMvc.perform(request("/method/limited", "203.0.113.7")).andExpect(status().isOk());
        this.mockMvc.perform(request("/method/limited", "203.0.113.7")).andExpect(status().isTooManyRequests());

        this.mockMvc.perform(request("/method/limited", "203.0.113.8")).andExpect(status().isOk());
    }

    @Test
    void anUnresolvableCallerIsRejected() throws Exception {
        this.mockMvc.perform(get("/method/limited")).andExpect(status().isTooManyRequests());
    }

    private static MockHttpServletRequestBuilder request(final String path, final String address) {
        return get(path).header("X-Forwarded-For", address);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        @Bean
        public MethodController methodController() {
            return new MethodController();
        }

        @Bean
        public SharedTypeController sharedTypeController() {
            return new SharedTypeController();
        }

        @Bean
        public SharedMethodController sharedMethodController() {
            return new SharedMethodController();
        }
    }

    @RestController
    @RequestMapping("/method")
    static class MethodController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
        @GetMapping("/limited")
        public ResponseEntity<Void> limited() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/open")
        public ResponseEntity<Void> open() {
            return ResponseEntity.ok().build();
        }
    }

    @RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
    @RestController
    @RequestMapping("/shared-type")
    static class SharedTypeController {

        @GetMapping("/first")
        public ResponseEntity<Void> first() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/second")
        public ResponseEntity<Void> second() {
            return ResponseEntity.ok().build();
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
        @GetMapping("/overridden")
        public ResponseEntity<Void> overridden() {
            return ResponseEntity.ok().build();
        }
    }

    @RateLimitShared(target = RateLimitTarget.METHOD, scope = RateLimitScope.IP_ADDRESS, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
    @RestController
    @RequestMapping("/shared-method")
    static class SharedMethodController {

        @GetMapping("/first")
        public ResponseEntity<Void> first() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/second")
        public ResponseEntity<Void> second() {
            return ResponseEntity.ok().build();
        }
    }
}