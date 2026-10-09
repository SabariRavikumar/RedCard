package com.zero.redcard.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.zero.redcard.proxy.BlocklistService;
import com.zero.redcard.records.BlocklistRequest;
import com.zero.redcard.records.RemoveRequest;

import reactor.core.publisher.Mono;


@Controller
@RequestMapping ("/admin")
public class AdminController {

    private final BlocklistService blocklistService;

    public AdminController(BlocklistService blocklistService) {
        this.blocklistService = blocklistService;
    }
    
    @GetMapping()
    public Mono<String> admin(Model model) {
        return blocklistService.listAllBlockedSites()
                .collectList()
                .doOnNext(sites -> model.addAttribute("sites", sites))
                .thenReturn("admin");
    }

    @PostMapping("/add")
    public Mono<String> addSite(@ModelAttribute BlocklistRequest request) {
        return blocklistService.addSite(request.host(), request.unlockSeconds())
                .thenReturn("redirect:/admin")
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just("redirect:/admin?error=invalid"));

    }

    @PostMapping("/remove")
    public Mono<String> removeSite(@ModelAttribute RemoveRequest request) {
        return blocklistService.removeSite(request.host())
                .thenReturn("redirect:/admin");
    }

    @PostMapping("/unlock")
    public Mono<String> unlockSite(@ModelAttribute RemoveRequest request) {
        return blocklistService.unlockSite(request.host())
                .thenReturn("redirect:/admin");
    }
    
}
