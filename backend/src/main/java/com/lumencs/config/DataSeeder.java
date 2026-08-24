package com.lumencs.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.auth.AdminUser;
import com.lumencs.auth.AdminUserMapper;
import com.lumencs.knowledge.KbDocument;
import com.lumencs.knowledge.KbDocumentMapper;
import com.lumencs.knowledge.KnowledgeService;
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
                "理财产品A说明",
                "product_faq.md",
                "我们的理财产品A年化收益率为3.5%-5.2%，投资期限为6个月至3年，最低投资金额10000元。注意：理财非存款，产品有风险，投资须谨慎。以上信息仅供参考，具体以合同条款为准。"
        );
        knowledgeService.ingest(
                "退款政策",
                "refund_policy.md",
                "退款政策：用户在购买后7天内可申请无理由退款，超过7天需提供合理原因。退款将在3-5个工作日内原路退回。大额退款可能需要人工审核并创建工单。"
        );
        knowledgeService.ingest(
                "开户流程",
                "account_guide.md",
                "开户流程：1.准备身份证原件 2.填写开户申请表 3.进行视频认证 4.设置交易密码 5.完成风险评估问卷。整个流程约需15-30分钟。"
        );
        log.info("seeded default knowledge documents");
    }
}
