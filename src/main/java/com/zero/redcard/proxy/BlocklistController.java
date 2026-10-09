package com.zero.redcard.proxy;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zero.redcard.records.BlocklistEntry;
import com.zero.redcard.records.BlocklistRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@RestController()
@RequestMapping ("/api/blocked")
public class BlocklistController {

    private final BlocklistService blocklistService;

    public BlocklistController(BlocklistService blocklistService) {
        this.blocklistService = blocklistService;
    }



    @PostMapping("")
    public Mono<String> addBlockedSite(@RequestBody BlocklistRequest entity) {

        return blocklistService.addSite(entity.host(), entity.unlockSeconds())
                .map(isAdded -> isAdded ? "Site Added to BlockList" : "Something Went Wrong");

    }
    
    @DeleteMapping("/{host}")
    public Mono<String> removeBlockedSite(@PathVariable String host) {

        return blocklistService.removeSite(host)
                .map(isRemoved -> isRemoved ? "Site Removed from BlockList" : "Something Went Wrong");
    }

    @GetMapping("")
    public Flux<BlocklistEntry> listBlockedSites() {
        return blocklistService.listAllBlockedSites();
    }
    
    
}
