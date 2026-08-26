package com.hengpick.mall;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LandingController {

    @GetMapping("/")
    Map<String, String> landing() {
        return Map.of(
                "service", "commerce-api",
                "status", "UP",
                "scope", "P01-S01");
    }
}

