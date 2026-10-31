package dev.queiroz.relay.operations;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.function.Supplier;

@Component
public class RelayTracing {
    public Span start(String name, String tenantId, String eventId) {
        Span span = GlobalOpenTelemetry.getTracer("relay").spanBuilder(name).startSpan();
        span.setAttribute("relay.tenant_id", tenantId);
        if (eventId != null) span.setAttribute("relay.event_id", eventId);
        return span;
    }
    public <T> T trace(String name, String tenantId, String eventId, Supplier<T> operation) {
        Span span = start(name, tenantId, eventId);
        try (Scope ignored = span.makeCurrent()) { return operation.get(); }
        catch (RuntimeException exception) { span.setStatus(StatusCode.ERROR); throw exception; }
        finally { span.end(); }
    }

    public HttpRequest.Builder inject(HttpRequest.Builder request) {
        ContextPropagators.create(io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance())
                .getTextMapPropagator().inject(io.opentelemetry.context.Context.current(), request, (carrier, key, value) -> carrier.header(key, value));
        return request;
    }
}
