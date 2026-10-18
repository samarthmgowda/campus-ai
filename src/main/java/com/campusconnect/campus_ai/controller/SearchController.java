package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchService.SearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return searchService.search(q, limit);
    }
}