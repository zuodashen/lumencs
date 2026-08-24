package com.lumencs.modules.memory;

import com.lumencs.chat.ChatSession;
import com.lumencs.chat.ChatSessionMapper;
import com.lumencs.common.R;
import com.lumencs.knowledge.KnowledgeService;
import com.lumencs.memory.LongTermMemoryService;
import com.lumencs.memory.ShortTermMemoryService;
import com.lumencs.memory.WorkingMemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/memory")
public class MemoryController {

    private final WorkingMemoryService workingMemory;
    private final ShortTermMemoryService shortTermMemory;
    private final LongTermMemoryService longTermMemory;
    private final KnowledgeService knowledgeService;
    private final ChatSessionMapper sessionMapper;

    public MemoryController(
            WorkingMemoryService workingMemory,
            ShortTermMemoryService shortTermMemory,
            LongTermMemoryService longTermMemory,
            KnowledgeService knowledgeService,
            ChatSessionMapper sessionMapper) {
        this.workingMemory = workingMemory;
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
        this.knowledgeService = knowledgeService;
        this.sessionMapper = sessionMapper;
    }

    @GetMapping
    public R<Map<String, Object>> inspect(@RequestParam String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        String userLabel = session == null || session.getUserLabel() == null ? "访客" : session.getUserLabel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("working", Map.of(
                "desc", "当前办事槽位 / 待提交卡片 / 最近意图（Redis Hash，TTL 30min）",
                "data", workingMemory.snapshot(sessionId)
        ));
        body.put("shortTerm", Map.of(
                "desc", "多轮对话窗口，会注入 RAG Prompt；短追问会拼到检索词",
                "data", shortTermMemory.history(sessionId)
        ));
        body.put("longTerm", Map.of(
                "desc", "用户画像（口味/工位）+ 知识库文档。画像用于卡片预填。",
                "documentCount", knowledgeService.listDocuments().size(),
                "profile", longTermMemory.profile(userLabel),
                "userLabel", userLabel
        ));
        return R.success(body);
    }
}
