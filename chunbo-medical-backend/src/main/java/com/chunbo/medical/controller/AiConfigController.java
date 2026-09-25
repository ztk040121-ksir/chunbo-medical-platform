package com.chunbo.medical.controller;

import com.chunbo.medical.dto.AiModelConfigDto;
import com.chunbo.medical.entity.SysAiConfig;
import com.chunbo.medical.service.AiModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/list")
    public List<SysAiConfig> list() {
        return configService.getAllConfigs();
    }

    @PostMapping("/switch/{id}")
    public AiModelConfigDto switchConfig(@PathVariable("id") Long id) {
        return configService.switchConfig(id);
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody AiModelConfigDto config) {
        return configService.testConnectivity(config);
    }
}
