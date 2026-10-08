package me.trae.foundation.spring.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "foundation")
public class FoundationProperties {

    private boolean production = false;
}