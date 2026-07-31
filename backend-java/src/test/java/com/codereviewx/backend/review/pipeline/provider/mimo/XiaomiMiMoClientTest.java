package com.codereviewx.backend.review.pipeline.provider.mimo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class XiaomiMiMoClientTest {

    @Test
    void complete_returnsAssistantContent() {
        XiaomiMiMoProperties properties = new XiaomiMiMoProperties();
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setModel("mimo-v2.5-pro");
        properties.setApiKey("test-key");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"model":"mimo-v2.5-pro","max_completion_tokens":2048}
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "[]"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        XiaomiMiMoClient client = new XiaomiMiMoClient(properties, builder.build());

        String content = client.complete("system", "user");

        assertThat(content).isEqualTo("[]");
        server.verify();
    }

    @Test
    void complete_appliesBoundedPlannerBudget() {
        XiaomiMiMoProperties properties = new XiaomiMiMoProperties();
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setModel("mimo-v2.5-pro");
        properties.setApiKey("test-key");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(content().json("""
                        {"max_completion_tokens":1024}
                        """, false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"{}"}}]}
                        """, MediaType.APPLICATION_JSON));

        XiaomiMiMoClient client = new XiaomiMiMoClient(properties, builder.build());

        assertThat(client.complete(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT, "user")).isEqualTo("{}");
        server.verify();
    }

    @Test
    void complete_allowsLargerExecutorBudget() {
        XiaomiMiMoProperties properties = new XiaomiMiMoProperties();
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setModel("mimo-v2.5-pro");
        properties.setApiKey("test-key");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(content().json("""
                        {"max_completion_tokens":2048}
                        """, false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"{}"}}]}
                        """, MediaType.APPLICATION_JSON));

        XiaomiMiMoClient client = new XiaomiMiMoClient(properties, builder.build());

        assertThat(client.complete(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT, "user")).isEqualTo("{}");
        server.verify();
    }

    @Test
    void complete_throwsWhenApiKeyMissing() {
        XiaomiMiMoProperties properties = new XiaomiMiMoProperties();
        properties.setApiKey("");

        XiaomiMiMoClient client = new XiaomiMiMoClient(properties);

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(XiaomiMiMoClientException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void complete_throwsOnNon2xxResponse() {
        XiaomiMiMoProperties properties = new XiaomiMiMoProperties();
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setModel("mimo-v2.5-pro");
        properties.setApiKey("test-key");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest());

        XiaomiMiMoClient client = new XiaomiMiMoClient(properties, builder.build());

        assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(XiaomiMiMoClientException.class)
                .hasMessageContaining("HTTP 400");
        server.verify();
    }

    @Test
    void complete_retriesOneTransientServerFailure() {
        XiaomiMiMoProperties properties = new XiaomiMiMoProperties();
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setModel("mimo-v2.5-pro");
        properties.setApiKey("test-key");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withServerError());
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"{}"}}]}
                        """, MediaType.APPLICATION_JSON));

        XiaomiMiMoClient client = new XiaomiMiMoClient(properties, builder.build());

        assertThat(client.complete("system", "user")).isEqualTo("{}");
        server.verify();
    }
}
