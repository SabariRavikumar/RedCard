package com.zero.redcard.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;



@RestController
public class PingPongController {
    @GetMapping("/ping")
    public Mono<String> ping() {
        System.out.println("ping " + Thread.currentThread().getName() + " at " + java.time.Instant.now());
        return Mono.just("pong");
    }

    @GetMapping("/string-ping")
    public String stringPing() {
        return "pong";
    }

    @GetMapping("/slow-mono")
    public Mono<String> slowMono() {
        Mono<String> mono = Mono.just("pong").delayElement(java.time.Duration.ofSeconds(5));
        System.out.println("slow-mono " +Thread.currentThread().getName() + " at " + java.time.Instant.now());
        return mono;
    }

    @GetMapping("/slow-block")
    public String slowBlock() throws InterruptedException {
        String result = "pong-block";
        Thread.sleep(java.time.Duration.ofSeconds(5));
        System.out.println("slow-block " + Thread.currentThread().getName() + " at " + java.time.Instant.now());
        return result;
    }
    
    

}