package me.trae.foundation.spring.pages;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "foundation.pages")
public class PageProperties {

    private String loginPath = "/auth";
    private String redirectParameter = "redirect";

    private boolean trailingSlashRedirect = true;

    private String robotsTag = "";

    private int pageSize = 10;
    private int paginationWindowSize = 10;

    private List<String> systemPathList = List.of("/api", "/stream");
    private List<String> robotsTagExcludedPathList = Collections.emptyList();
}