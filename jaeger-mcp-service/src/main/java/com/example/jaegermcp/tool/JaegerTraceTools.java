package com.example.jaegermcp.tool;

import com.example.jaegermcp.config.JaegerClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class JaegerTraceTools {

    private final RestTemplate restTemplate;
    private final JaegerClientConfig jaegerClientConfig;

    @Autowired
    public JaegerTraceTools(RestTemplate restTemplate, JaegerClientConfig jaegerClientConfig) {
        this.restTemplate = restTemplate;
        this.jaegerClientConfig = jaegerClientConfig;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "get_trace_by_id", description = "Fetch a trace by trace ID from Jaeger. The traceId parameter is the trace ID to look up.")
    public String getTraceById(String traceId) {
        try {
            String url = jaegerClientConfig.getJaegerBaseUrl() + "/api/traces/" + traceId;
            return restTemplate.getForObject(url, String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return "Trace not found";
        } catch (Exception e) {
            return "Error fetching trace: " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(name = "search_traces", description = "Search traces in Jaeger by service and time window. Parameters: service (service name), start (Unix microseconds), end (Unix microseconds), limit (max traces).")
    public String searchTraces(String service, Long start, Long end, Integer limit) {
        try {
            String url = jaegerClientConfig.getJaegerBaseUrl() + "/api/traces?service=" + service 
                    + "&start=" + start + "&end=" + end + "&limit=" + limit;
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return "Error searching traces: " + e.getMessage();
        }
    }
}