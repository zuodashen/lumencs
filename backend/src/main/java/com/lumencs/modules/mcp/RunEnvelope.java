package com.lumencs.modules.mcp;

import java.util.Set;

/**
 * 本轮办事信封：只暴露当前意图的工具，写出最多一次。
 */
public record RunEnvelope(String intent, Set<String> allowedTools) {
}
