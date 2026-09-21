package com.chunbo.medical.service;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

/**
 * 语音与文字互转服务（参照《SpringAI》笔记语音文字互转）
 * TTS：OpenAiAudioSpeechModel（tts-1）+ ResponseBodyEmitter 流式输出 mp3 音频
 * ASR：OpenAiAudioTranscriptionModel（whisper-1）语音识别为文字
 * 配置走 ohmygpt 中转站（application.yml spring.ai.openai.audio.*）
 * 注：Spring AI 1.0.0 GA 的语音类位于 org.springframework.ai.openai.audio.* 与 org.springframework.ai.audio.transcription.*
 */
@Service
public class AudioService {

    @Autowired
    private OpenAiAudioSpeechModel speechModel;

    @Autowired
    private OpenAiAudioTranscriptionModel transcriptionModel;

    /**
     * 文字转语音（TTS 流式）：把合成的 mp3 音频块通过 ResponseBodyEmitter 推给前端
     */
    public ResponseBodyEmitter ttsStream(String text) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        try {
            SpeechPrompt prompt = new SpeechPrompt(text);
            Flux<SpeechResponse> flux = speechModel.stream(prompt);
            flux.subscribe(
                    response -> {
                        try {
                            byte[] audio = response.getResult().getOutput();
                            if (audio != null && audio.length > 0) {
                                emitter.send(audio);
                            }
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    emitter::completeWithError,
                    emitter::complete
            );
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 语音转文字（ASR）：上传音频文件，whisper-1 识别为文字
     * 显式指定 model/language/prompt——whisper 默认语言自动检测，浏览器短录音常被误判为英文随机输出
     */
    public String asr(MultipartFile file) {
        try {
            // 一次性读入字节：MultipartFile 流只能消费一次，落盘副本与识别共用这份数据
            // （上一版先 transferTo 落盘再 getResource() 读流，临时文件被移走导致 500）
            byte[] audioBytes = file.getBytes();
            System.out.println("[ASR] 收到音频文件: " + file.getOriginalFilename()
                    + " 大小=" + audioBytes.length + "字节 类型=" + file.getContentType());
            // 调试副本：把上传的录音落盘（保留最近5份），可直接用播放器听，确诊"静音还是识别不准"
            try {
                java.io.File dir = new java.io.File("asr_debug");
                if (!dir.exists()) dir.mkdirs();
                java.io.File[] olds = dir.listFiles();
                if (olds != null && olds.length >= 5) {
                    java.util.Arrays.sort(olds, java.util.Comparator.comparingLong(java.io.File::lastModified));
                    olds[0].delete();
                }
                java.nio.file.Files.write(new java.io.File(dir, "asr_" + System.currentTimeMillis() + ".webm").toPath(), audioBytes);
                System.out.println("[ASR] 调试副本已保存至 asr_debug/");
            } catch (Exception debugEx) {
                System.out.println("[ASR] 调试副本保存失败: " + debugEx.getMessage());
            }
            org.springframework.ai.openai.OpenAiAudioTranscriptionOptions options =
                    org.springframework.ai.openai.OpenAiAudioTranscriptionOptions.builder()
                            .model("whisper-1")
                            .language("zh")
                            .prompt("以下是基层门诊医生与患者口述的中文症状、用药对话。")
                            .build();
            // 识别资源必须带文件名（whisper 按扩展名判断音频格式）
            org.springframework.core.io.ByteArrayResource audioResource = new org.springframework.core.io.ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "voice.webm";
                }
            };
            // 代理链路（VPN→ohmygpt）偶发 TLS 握手中断，自动重试 3 次
            String text = null;
            Exception lastEx = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);
                    AudioTranscriptionResponse response = transcriptionModel.call(prompt);
                    text = response.getResult().getOutput();
                    break;
                } catch (Exception callEx) {
                    lastEx = callEx;
                    System.out.println("[ASR] 第" + attempt + "次调用失败: " + callEx.getMessage());
                    if (attempt < 3) {
                        try { Thread.sleep(1200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }
            if (text == null) {
                throw new RuntimeException("语音识别失败(已重试3次): " + lastEx.getMessage(), lastEx);
            }
            // 繁体转简体（whisper 偶发输出繁体中文，参照《SpringAI》笔记语音转文字标准实现）
            text = com.github.houbb.opencc4j.util.ZhConverterUtil.toSimple(text);
            System.out.println("[ASR] 识别结果: " + text);
            // 静音/极低音量音频会让 whisper "幻听"出字幕网站通用句（如"由 Amara.org 社区搜索的字幕"），
            // 命中幻听特征或结果为空时视为无效语音，返回 null 由 Controller 给出明确提示
            if (isInvalidRecognition(text)) {
                System.out.println("[ASR] 判定为无效识别（静音/幻听），拒绝返回: " + text);
                return null;
            }
            return text;
        } catch (Exception e) {
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        }
    }

    /** whisper 幻听特征句（训练语料里的字幕网站/频道/新闻套话），命中即视为无效识别 */
    private static final String[] HALLUCINATION_PATTERNS = {
            "amara", "字幕由", "的字幕", "字幕组", "subtitles", "红十字会",
            "谢谢观看", "感谢观看", "for watching", "subscribe", "请订阅",
            "明镜", "点点栏目", "请不吝点赞", "翻译不可使用", "不得翻译",
            "请勿模仿", "请勿尝试", "请勿轻信", "ming pao", "收睇",
            "时局新闻", "再会!", "感谢您收"
    };

    /** 识别结果无效判定：空/过短，命中幻听特征句，或为全大写英文短语（真实中文问诊输入不会如此） */
    private boolean isInvalidRecognition(String text) {
        if (text == null) return true;
        String t = text.trim().replace(" ", "").toLowerCase();
        if (t.length() < 2) return true;
        for (String p : HALLUCINATION_PATTERNS) {
            if (t.contains(p)) return true;
        }
        // 全大写英文短语幻听（如 "MING PAO CANADA"）：医疗问诊场景用户不会输入全大写英文
        String raw = text.trim();
        if (raw.matches("[A-Z0-9\\s\\p{Punct}/]+") && raw.matches(".*[A-Z]{3,}.*")) {
            return true;
        }
        return false;
    }
}
