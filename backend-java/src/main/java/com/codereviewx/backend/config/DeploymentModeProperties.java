package com.codereviewx.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codereviewx")
public class DeploymentModeProperties {

    private DeploymentMode deploymentMode = DeploymentMode.SELF_HOST;

    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(DeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public enum DeploymentMode {
        SELF_HOST
    }
}
