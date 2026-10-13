package com.zero.redcard.puzzle;

import com.zero.redcard.admin.AdminController;
import java.util.Random;
import java.util.UUID;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import com.zero.redcard.records.Puzzle;

import reactor.core.publisher.Mono;

@Service 
public class PuzzleService {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String PUZZLE_KEY = "puzzle:";

    public PuzzleService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Puzzle> createPuzzle() {
        Random random = new Random();
        Integer op1 = random.nextInt(1, 10000);
        Integer op2 = random.nextInt(1, 10000);
        String uuid = UUID.randomUUID().toString();

        StringBuilder strBuild = new StringBuilder();
        strBuild.append(op1);
        strBuild.append("+");
        strBuild.append(op2);

        String question = strBuild.toString();
        String ans = Integer.toString(op1 + op2);

        Puzzle puzzle = new Puzzle(uuid, question);

        String key = PUZZLE_KEY + uuid;

        return redisTemplate.opsForValue().set(key, ans, java.time.Duration.ofMinutes(5l)).thenReturn(puzzle);

    }
    
    public Mono<Boolean> verify(String id, String submittedAnswer) {
        String key = PUZZLE_KEY + id;
        if (submittedAnswer == null) {
            return Mono.just(false);
        }
        return redisTemplate.opsForValue().getAndDelete(key).map(val -> val.equals(submittedAnswer.trim()))
                .defaultIfEmpty(false);
    }
}
