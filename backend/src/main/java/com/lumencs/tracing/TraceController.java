package com.lumencs.tracing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/traces")
public class TraceController {

    private final TraceSpanMapper spanMapper;

    public TraceController(TraceSpanMapper spanMapper) {
        this.spanMapper = spanMapper;
    }

    @GetMapping
    public R<List<TraceSpan>> list(@RequestParam String sessionId) {
        return ApiResponse.ok(spanMapper.selectList(new LambdaQueryWrapper<TraceSpan>()
                .eq(TraceSpan::getSessionId, sessionId)
                .orderByAsc(TraceSpan::getId)));
    }
}
