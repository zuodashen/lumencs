package com.lumencs.modules.blogwrite;

public final class BlogWriteGuard {

    public static final String LOGIN_HINT = """
            写博客、加书签或新建标签需要先登录中枢控制台，登录后再回到本页发送。
            打开：/console/login （默认 admin / lumen123）
            """.strip();

    public static final String TOKEN_HINT = "卡片确认令牌无效或已使用，请重新说一次需求，等新卡片出现后再提交。";

    private BlogWriteGuard() {}

    public static boolean isWriteIntent(String intent) {
        return intent != null && intent.startsWith("blog_");
    }
}
