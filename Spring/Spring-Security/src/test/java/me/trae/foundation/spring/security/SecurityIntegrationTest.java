package me.trae.foundation.spring.security;

import jakarta.servlet.http.Cookie;
import me.trae.foundation.spring.common.constants.FilterOrderConstants;
import me.trae.foundation.spring.security.csrf.annotation.CsrfExclude;
import me.trae.foundation.spring.security.csrf.filter.CsrfFilter;
import me.trae.foundation.spring.security.csrf.service.CsrfTokenService;
import me.trae.foundation.spring.security.headers.SecurityHeadersFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "foundation.production=false",
        "foundation.security.headers.script-src[0]='self'",
        "foundation.security.headers.script-src[1]=https://cdn.example.com"
})
final class SecurityIntegrationTest {

    private static final String TOKEN = "a-token-value";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CsrfTokenService csrfTokenService;

    @Test
    void securityHeadersAreOnEveryResponse() throws Exception {
        this.mockMvc.perform(get("/page"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void theConfiguredScriptSourcesReachThePolicy() throws Exception {
        final String policy = this.mockMvc.perform(get("/page")).andReturn().getResponse().getHeader("Content-Security-Policy");

        assertTrue(policy.contains("script-src 'self' https://cdn.example.com"));
    }

    @Test
    void headersAreStillOnANotFound() throws Exception {
        this.mockMvc.perform(get("/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void hstsIsAbsentOutsideProduction() throws Exception {
        this.mockMvc.perform(get("/page")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void aSafeRequestIssuesTheCsrfCookie() throws Exception {
        final String setCookie = this.mockMvc.perform(get("/page")).andReturn().getResponse().getHeader("Set-Cookie");

        assertTrue(setCookie.contains("%s=".formatted(this.csrfTokenService.getCookieName())));
    }

    @Test
    void aWriteWithoutATokenIsRejected() throws Exception {
        this.mockMvc.perform(post("/page/save").header("Origin", "http://localhost")).andExpect(status().isForbidden());
    }

    @Test
    void aWriteWithAMatchingTokenPassesThrough() throws Exception {
        this.mockMvc.perform(post("/page/save")
                        .header("Origin", "http://localhost")
                        .header("X-XSRF-TOKEN", TOKEN)
                        .cookie(new Cookie(this.csrfTokenService.getCookieName(), TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void aWriteFromAForeignOriginIsRejected() throws Exception {
        this.mockMvc.perform(post("/page/save")
                        .header("Origin", "https://attacker.example.net")
                        .header("X-XSRF-TOKEN", TOKEN)
                        .cookie(new Cookie(this.csrfTokenService.getCookieName(), TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anExcludedControllerTakesWritesWithNoToken() throws Exception {
        this.mockMvc.perform(post("/webhook/payment")).andExpect(status().isOk());
    }

    @Test
    void anExcludedPathDoesNotCoverItsNeighbours() throws Exception {
        this.mockMvc.perform(post("/page/save")).andExpect(status().isForbidden());
    }

    @Test
    void bothFiltersAreRegisteredInOrder() {
        final FilterRegistrationBean<?> headers = this.registration(SecurityHeadersFilter.class);
        final FilterRegistrationBean<?> csrf = this.registration(CsrfFilter.class);

        assertEquals(FilterOrderConstants.SECURITY_HEADERS, headers.getOrder());
        assertEquals(FilterOrderConstants.CSRF, csrf.getOrder());
        assertTrue(headers.getOrder() < csrf.getOrder());
    }

    private FilterRegistrationBean<?> registration(final Class<?> filterType) {
        for (final FilterRegistrationBean<?> registrationBean : this.applicationContext.getBeansOfType(FilterRegistrationBean.class).values()) {
            if (!filterType.isInstance(registrationBean.getFilter())) {
                continue;
            }

            return registrationBean;
        }

        throw new IllegalStateException("No registration for %s".formatted(filterType.getName()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        @Bean
        public PageController pageController() {
            return new PageController();
        }

        @Bean
        public WebhookController webhookController() {
            return new WebhookController();
        }
    }

    @RestController
    @RequestMapping("/page")
    static class PageController {

        @GetMapping
        public ResponseEntity<Void> render() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/save")
        public ResponseEntity<Void> save() {
            return ResponseEntity.ok().build();
        }
    }

    @CsrfExclude
    @RestController
    @RequestMapping("/webhook")
    static class WebhookController {

        @PostMapping("/payment")
        public ResponseEntity<Void> payment() {
            return ResponseEntity.ok().build();
        }
    }
}