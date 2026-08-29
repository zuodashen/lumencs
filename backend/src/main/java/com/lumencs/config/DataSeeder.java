package com.lumencs.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.model.entity.AdminUser;
import com.lumencs.mapper.AdminUserMapper;
import com.lumencs.model.entity.KbDocument;
import com.lumencs.mapper.KbDocumentMapper;
import com.lumencs.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final AdminUserMapper adminUserMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeService knowledgeService;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataSeeder(
            AdminUserMapper adminUserMapper,
            KbDocumentMapper documentMapper,
            KnowledgeService knowledgeService,
            PasswordEncoder passwordEncoder,
            @Value("${lumencs.admin.username}") String adminUsername,
            @Value("${lumencs.admin.password}") String adminPassword) {
        this.adminUserMapper = adminUserMapper;
        this.documentMapper = documentMapper;
        this.knowledgeService = knowledgeService;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedKnowledge();
    }

    private void seedAdmin() {
        Long count = adminUserMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(admin);
        log.info("seeded admin user {}", adminUsername);
    }

    private void seedKnowledge() {
        Long count = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>());
        if (count != null && count > 0) {
            return;
        }
        knowledgeService.ingest(
                "本机 Docker 注意",
                "java-dev.md",
                """
                本机用 OrbStack 跑 LumenCS。改 Java 代码后需要 docker compose up -d --build backend，慢在镜像里重新 mvn package，不是重新拉 MySQL 镜像。
                只改 .env 用 --force-recreate backend，不要加 --build。
                博客写入走 LightDiary 管理 API，不要直连博客 MySQL。
                """
        );
        knowledgeService.ingest(
                "个人作息备忘",
                "life.md",
                """
                工作日上午写代码，下午开会。晚上 11 点后不看工单。
                常去的咖啡：生椰拿铁少糖少冰。工位在 A 区靠窗。
                问「我几点睡觉」时如果知识库没有更新，就说以这条备忘为准。
                """
        );
        knowledgeService.ingest(
                "管家能做什么",
                "hub.md",
                """
                个人 AI 中枢可以：回答已上传的笔记、把一句话记进知识库、加待办、写博客草稿、点一杯演示奶茶。
                不是银行客服，不办理开户退款。写博客需要先登录中枢控制台。
                """
        );
        log.info("seeded default knowledge documents");
    }
}
