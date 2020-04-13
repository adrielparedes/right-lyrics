package io.rl.song.opentracing;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;


public class RestTemplateOpentracingInterceptor implements ClientHttpRequestInterceptor {

    private final Tracer tracer;

    public RestTemplateOpentracingInterceptor(Tracer tracer){
        this.tracer = tracer;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        Span span = tracer.buildSpan(request.getMethod().toString())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();

        tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
                new HttpHeadersCarrier(request.getHeaders()));

        ClientHttpResponse httpResponse;

        try (Scope scope = tracer.activateSpan(span)) {
             httpResponse = execution.execute(request, body);
        } catch (Exception ex) {
            throw ex;
        } finally {
            span.finish();
        }
        return httpResponse;
    }
}