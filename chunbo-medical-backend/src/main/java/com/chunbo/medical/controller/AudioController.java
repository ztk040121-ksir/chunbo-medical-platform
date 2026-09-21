package com.chunbo.medical.controller;

import com.chunbo.medical.service.AudioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;

/**
 * 语音文字互转 Controller（参照《SpringAI》笔记语音文字互转）
 * TTS：POST /api/audio/tts-stream  body 为待合成文本，流式返回 audio/mp3
 * ASR：POST /api/audio/asr  上传音频文件，返回识别文字
 */
@RestController
@RequestMapping("/api/audio")
public class AudioController {

    @Autowired
    private AudioService audioService;

    /**
     * 文字转语音（TTS 流式输出 mp3）
     * POST /api/audio/tts-stream  Content-Type: text/plain  body: 待合成文本
     */
    @PostMapping(value = "/tts-stream", produces = "audio/mp3")
    public ResponseBodyEmitter ttsStream(@RequestBody String text) {
        return audioService.ttsStream(text);
    }

    /**
     * 语音转文字（ASR）
     * POST /api/audio/asr  multipart/form-data  file: 音频文件(mp3/wav/m4a等)
     * 静音/幻听等无效识别时返回 success=false + message（前端给出明确重试指引）
     */
    @PostMapping(value = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> asr(@RequestParam("file") MultipartFile file) {
        String text = audioService.asr(file);
        if (text == null) {
            return Map.of("success", false,
                    "message", "未检测到有效语音：录音音量过低或基本静音。请靠近麦克风大声说，"
                            + "并在 系统设置→声音→输入 中把麦克风音量调到 80 以上后重试");
        }
        return Map.of("success", true, "text", text);
    }
}
