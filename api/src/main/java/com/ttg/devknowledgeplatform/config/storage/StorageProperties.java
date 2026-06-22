package com.ttg.devknowledgeplatform.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "app.storage")
@Data
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private int presignedUrlExpiryMinutes = 60;
}
