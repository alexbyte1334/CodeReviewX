package com.codereviewx.backend.demo;

import com.codereviewx.backend.demo.DemoDtos.PublishRequest;
import com.codereviewx.backend.demo.DemoDtos.PublishResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/demo-runs")
public class DemoAdminController {
    private final DemoAdminPublishService publisher;

    public DemoAdminController(DemoAdminPublishService publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/{publicId}/publish")
    public PublishResponse publish(
            @PathVariable String publicId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody PublishRequest request) {
        return publisher.publish(publicId, authorization,
                request == null ? null : request.selectedPreviewIds());
    }
}
