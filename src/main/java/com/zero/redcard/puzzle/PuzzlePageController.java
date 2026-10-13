package com.zero.redcard.puzzle;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import com.zero.redcard.proxy.BlocklistService;
import com.zero.redcard.records.SolveRequest;

import reactor.core.publisher.Mono;



@Controller
@RequestMapping("/puzzle")
public class PuzzlePageController {

    private final PuzzleService puzzleService;
    private final BlocklistService blocklistService;

    public PuzzlePageController(PuzzleService puzzleService, BlocklistService blocklistService) {
        this.puzzleService = puzzleService;
        this.blocklistService = blocklistService;
    }

    @GetMapping()
    public Mono<String> createPuzzle(@RequestParam String host, @RequestParam String url, Model model) {
        return puzzleService.createPuzzle()
                .doOnNext(puzzle -> {
                    model.addAttribute("puzzle", puzzle);
                    model.addAttribute("url", url);
                    model.addAttribute("host", host);
                })
                .thenReturn("puzzle");
    }
    
    @PostMapping("/solve")
    public Mono<String> solve(@ModelAttribute SolveRequest entity) {
        return puzzleService.verify(entity.id(), entity.answer())
                .flatMap(correct -> {
                    if (correct) {
                        return blocklistService.unlockSite(entity.host())
                                .thenReturn("redirect:" + safeTarget(entity));
                    }
                    return Mono.just("redirect:/puzzle?host=" + encode(entity.host())
                            + "&url=" + encode(entity.url()));
                });
    }

    private static String encode(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private static String safeTarget(SolveRequest request) {
        try {
            URI target = URI.create(request.url());
            boolean web = "http".equals(target.getScheme()) || "https".equals(target.getScheme());
            if (web && target.getHost() != null && target.getHost().equalsIgnoreCase(request.host())) {
                return request.url();
            }
        } catch (IllegalArgumentException e) {
            // malformed URL: use the safe default below
        }
        return "/admin";
    }

    
    
    
}
