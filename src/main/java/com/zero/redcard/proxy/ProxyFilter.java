package com.zero.redcard.proxy;

import java.util.Set;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component 
public class ProxyFilter implements WebFilter {
    private final HttpClient httpClient = HttpClient.create().compress(false);
    private final WebClient webClient = WebClient.builder()
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .build();
    private final static Set<String> HOP_BY_HOP_HEADERS = Set.of(
        "connection", 
        "keep-alive", 
        "proxy-authenticate", 
        "proxy-authorization", 
        "te", "trailer", 
        "transfer-encoding", 
        "upgrade",
        "host",
        "proxy-connection"
    );

    private final BlocklistService blocklistService;

    public ProxyFilter(BlocklistService blocklistService) {
        this.blocklistService = blocklistService;
    }
    

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String host = exchange.getRequest().getURI().getHost();
        if (method.matches("CONNECT")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_IMPLEMENTED);
            return exchange.getResponse().setComplete();
        }
        if (host.equalsIgnoreCase("localhost")) {
            return chain.filter(exchange);
        }

        return blocklistService.isBlocked(host)
                .defaultIfEmpty(false)
                .flatMap(
                        isBlocked -> isBlocked ? handleBlockedRequest(exchange, host) : forwardRequest(exchange, host));
    }
    
    private Mono<Void> handleBlockedRequest(ServerWebExchange exchange, String host) {
        Mono<DataBuffer> errorBody = Mono
                .just(exchange.getResponse().bufferFactory().wrap("Blocked by RedCard".getBytes()));
        System.out.println("Blocked request to " + host);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
        return exchange.getResponse().writeWith(errorBody);
    }

    private Mono<Void> forwardRequest(ServerWebExchange exchange, String host) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        Flux<DataBuffer> responseBody = webClient.method(method)
                .uri(request.getURI())
                .headers(outgoingHeaders -> {
                    request.getHeaders().forEach((headerName, headerValues) -> {
                        if (!HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                            outgoingHeaders.addAll(headerName, headerValues);
                        }
                    });
                })
                .body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToFlux(response -> {
                    exchange.getResponse().setStatusCode(response.statusCode());
                    response.headers().asHttpHeaders().forEach((headerName, headerValues) -> {
                        if (!HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                            exchange.getResponse().getHeaders().addAll(headerName, headerValues);
                        }
                    });
                    return response.bodyToFlux(DataBuffer.class);
                });

        return exchange.getResponse().writeWith(responseBody);
    }
    
}
