package com.chunbo.medical.controller;

import com.chunbo.medical.dto.AiModelConfigDto;
import com.chunbo.medical.service.AiModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/ai")
public class AiConfigController {

    @Autowired
    private AiModelConfigService configService;

    @GetMapping
    public AiModelConfigDto get() {
        return configService.getCurrentConfig();
    }

    @PostMapping
    public AiModelConfigDto update(@RequestBody AiModelConfigDto config) {
        configService.updateConfig(config);
        return configService.getCurrentConfig();
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody AiModelConfigDto config) {
        return configService.testConnectivity(config);
    }
}
