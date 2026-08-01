package com.codereviewx.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codereviewx")
public class DeploymentModeProperties {

    private DeploymentMode deploymentMode = DeploymentMode.PUBLIC_DEMO;

    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(DeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public boolean isPublicDemo() {
        return deploymentMode == DeploymentMode.PUBLIC_DEMO;
    }

    public enum DeploymentMode {
        PUBLIC_DEMO,
        SELF_HOST
    }
}
