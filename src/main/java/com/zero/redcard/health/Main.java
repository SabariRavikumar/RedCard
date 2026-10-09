package com.zero.redcard.health;

import reactor.core.publisher.Mono;

public class Main {
    public static void main(String[] args) {
        Mono<String> mono = Mono.just("reddit.com").map(url -> {
            url = url.toUpperCase();
            System.out.println("Mapping " + url);
            return url;
        });
        System.out.println("Processing " + mono.toString());
        mono.subscribe(url -> System.out.println("Subscribing " + url));

    }
    
}
