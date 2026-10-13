package com.zero.redcard.puzzle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zero.redcard.records.Puzzle;

import reactor.core.publisher.Mono;



@RestController
@RequestMapping("/puzzle")
public class PuzzleController {

    private final PuzzleService puzzleService;

    public PuzzleController(PuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }
    
    @GetMapping("/new")
    public Mono<Puzzle> getNewPuzzle() {
        return puzzleService.createPuzzle();
    }

    @PostMapping("/verify")
    public Mono<Boolean> postMethodName(@RequestParam String id, @RequestParam String ans) {
        
        return puzzleService.verify(id, ans);
    }
    

}
