package me.trae.foundation.spring.pages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import me.trae.foundation.spring.pages.role.PageRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "foundation.production=false",
        "foundation.pages.robots-tag=noindex, nofollow",
        "foundation.pages.robots-tag-excluded-path-list=/public"
})
final class PagesIntegrationTest {

    private static final PageRole MEMBER = () -> "MEMBER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageRegistry pageRegistry;

    @Autowired
    private PageAccessResolver pageAccessResolver;

    @Test
    void autoConfigurationRegistersPagesAndCustomAccessResolver() {
        assertEquals(2, this.pageRegistry.getPageList().size());
        assertSame(TestAccessResolver.class, this.pageAccessResolver.getClass());
        assertSame(MEMBER, this.pageRegistry.getAssetRole("/js/member.js"));
    }

    @Test
    void publicPageRendersWithItsCachePolicyAndRobotsExclusion() throws Exception {
        this.mockMvc.perform(get("/public"))
                .andExpect(status().isOk())
                .andExpect(content().string("public page"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, CacheControlData.PRIVATE_REVALIDATE.getValue()))
                .andExpect(header().doesNotExist("X-Robots-Tag"));
    }

    @Test
    void protectedPageRedirectsUnauthenticatedViewer() throws Exception {
        this.mockMvc.perform(get("/member").queryParam("tab", "billing"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/auth?redirect=%2Fmember%3Ftab%3Dbilling"));
    }

    @Test
    void protectedPageReturnsForbiddenWhenViewerLacksRole() throws Exception {
        this.mockMvc.perform(get("/member").header("X-Authenticated", "true"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, CacheControlData.PRIVATE_REVALIDATE.getValue()))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    }

    @Test
    void protectedPageRendersForViewerWithRole() throws Exception {
        this.mockMvc.perform(get("/member").header("X-Authenticated", "true").header("X-Member", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string("member page"));
    }

    @Test
    void nonCanonicalPagePathRedirectsPermanentlyAndKeepsQuery() throws Exception {
        this.mockMvc.perform(get("/public/").queryParam("page", "2"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string(HttpHeaders.LOCATION, "/public?page=2"));
    }

    @Test
    void protectedAssetIsHiddenFromViewerWithoutRole() throws Exception {
        this.mockMvc.perform(get("/js/member.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, CacheControlData.PRIVATE_NO_STORE.getValue()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        @Bean
        public Page publicPage() {
            return new PublicPage();
        }

        @Bean
        public Page memberPage() {
            return new MemberPage();
        }

        @Bean
        public PageAccessResolver testAccessResolver() {
            return new TestAccessResolver();
        }
    }

    static final class PublicPage extends Page {

        private PublicPage() {
            super("/public", null);
        }

        @Render
        @ResponseBody
        public String render() {
            return "public page";
        }
    }

    static final class MemberPage extends Page {

        private MemberPage() {
            super("/member", MEMBER);
        }

        @Override
        public Set<String> getAssetPaths() {
            return Set.of("/js/member.js");
        }

        @Render
        @ResponseBody
        public String render() {
            return "member page";
        }
    }

    static final class TestAccessResolver implements PageAccessResolver {

        @Override
        public boolean isAuthenticated(final HttpServletRequest request, final HttpServletResponse response) {
            return "true".equals(request.getHeader("X-Authenticated"));
        }

        @Override
        public boolean hasRole(final HttpServletRequest request, final HttpServletResponse response, final PageRole pageRole) {
            return "true".equals(request.getHeader("X-Member"));
        }
    }
}
