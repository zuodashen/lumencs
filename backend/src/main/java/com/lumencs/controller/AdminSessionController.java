package com.lumencs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import com.lumencs.mapper.ChatSessionMapper;
import com.lumencs.model.entity.ChatSession;
import com.lumencs.model.vo.SessionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final ChatSessionMapper sessionMapper;

    public AdminSessionController(ChatSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @GetMapping
    public R<List<SessionVO>> list() {
        List<SessionVO> records = sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .last("LIMIT 50"))
                .stream()
                .map(SessionVO::from)
                .toList();
        return ApiResponse.ok(records);
    }
}
