package com.projvault.pkc.file;

import com.projvault.pkc.project.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRelevanceServiceTest {

    private final ProjectRelevanceService service = new ProjectRelevanceService();

    @Test
    void marksOtherProjectFileOutOfScopeWithoutRelyingOnFolderName() {
        Project project = project();
        FileAsset file = file("03项目文档/湖南省地质找矿智慧服务项目人员.xlsx");
        String text = """
                湖南省地质找矿智慧服务项目人员安排
                项目经理：张三
                技术负责人：李四
                服务范围：找矿智慧服务平台建设。
                """;

        ProjectRelevanceService.RelevanceResult result = service.evaluate(project, file, text);

        assertThat(result.status()).isEqualTo(ProjectRelevanceService.OUT_OF_SCOPE);
        assertThat(result.scopeType()).isEqualTo(ProjectRelevanceService.SCOPE_OTHER_PROJECT);
        assertThat(result.reason()).contains("其他项目");
    }

    @Test
    void keepsCurrentProjectInterfaceDocumentInScopeEvenWhenItContainsExamples() {
        Project project = project();
        FileAsset file = file("03项目文档/07接口设计/湖南省地质院数字地质项目接口设计说明书-V1.0.docx");
        String text = """
                湖南省地质院数字地质项目接口设计说明书
                本文描述省地质一张图系统与统一认证中心、数据治理平台之间的接口边界。
                API 示例：
                GET http://192.168.1.1:8080/demo/users
                以上地址仅为示例，请以正式部署方案为准。
                """;

        ProjectRelevanceService.RelevanceResult result = service.evaluate(project, file, text);

        assertThat(result.status()).isEqualTo(ProjectRelevanceService.IN_SCOPE);
        assertThat(result.scopeType()).isEqualTo(ProjectRelevanceService.SCOPE_PROJECT_CORE);
        assertThat(result.score()).isGreaterThan(0.4);
    }

    @Test
    void keepsCurrentProjectTenderAndDatabaseDocumentsInScope() {
        Project project = project();
        FileAsset tender = file("01项目前期相关材料/湖南省地质院数字地质项目招标正文（定稿）(1).doc");
        String tenderText = """
                湖南省地质院数字地质项目招标正文
                省本级政府采购公开招标文件，投标人应针对本项目提供相关响应资料。
                本项目建设目标为省地质一张图能力升级。
                """;
        FileAsset database = file("03项目文档/03数据库/湖南省地质院数字地质项目数据库设计V1.0.docx");
        String databaseText = """
                湖南省地质院数字地质项目数据库设计
                统一门户认证平台、数据资源共享平台、运用设计和数据库环境说明。
                """;

        assertThat(service.evaluate(project, tender, tenderText).status())
                .isEqualTo(ProjectRelevanceService.IN_SCOPE);
        assertThat(service.evaluate(project, database, databaseText).status())
                .isEqualTo(ProjectRelevanceService.IN_SCOPE);
    }

    @Test
    void marksGenericTemplateAsReferenceWhenItLacksCurrentProjectAnchors() {
        Project project = project();
        FileAsset file = file("03项目文档/培训方案模板.docx");
        String text = "培训方案模板，仅供项目实施团队参考。";

        ProjectRelevanceService.RelevanceResult result = service.evaluate(project, file, text);

        assertThat(result.status()).isEqualTo(ProjectRelevanceService.REFERENCE);
        assertThat(result.scopeType()).isEqualTo(ProjectRelevanceService.SCOPE_REFERENCE_MATERIAL);
    }

    @Test
    void worksForGenericArchivePlatformProject() {
        Project project = new Project();
        project.setCode("star-archive");
        project.setName("星河档案平台");
        project.setRootPath("D:/projects/星河档案平台");

        FileAsset inScope = file("02设计文档/星河档案平台接口设计说明书.docx");
        String inScopeText = "星河档案平台接口设计说明书，描述档案入库、借阅审批和归档检索接口。";
        FileAsset other = file("03项目文档/云桥运维系统人员安排.xlsx");
        String otherText = "云桥运维系统人员安排，项目经理：王五，运维负责人：赵六。";

        assertThat(service.evaluate(project, inScope, inScopeText).status())
                .isEqualTo(ProjectRelevanceService.IN_SCOPE);
        assertThat(service.evaluate(project, other, otherText).status())
                .isEqualTo(ProjectRelevanceService.OUT_OF_SCOPE);
    }

    @Test
    void doesNotRejectCurrentFileOnlyBecauseBodyMentionsGenericProjectPhrases() {
        Project project = new Project();
        project.setCode("star-archive");
        project.setName("星河档案平台");
        project.setRootPath("D:/projects/星河档案平台");
        FileAsset file = file("00项目章程-星河档案平台.md");
        String text = """
                星河档案平台项目章程
                本平台用于工程项目资料治理、问答和验收辅助。
                资料里可能出现集团项目、服务项目、建设项目等泛化描述，这些不是其他项目名称。
                """;

        ProjectRelevanceService.RelevanceResult result = service.evaluate(project, file, text);

        assertThat(result.status()).isEqualTo(ProjectRelevanceService.IN_SCOPE);
        assertThat(result.scopeType()).isEqualTo(ProjectRelevanceService.SCOPE_PROJECT_CORE);
    }

    @Test
    void worksForGenericOpsProject() {
        Project project = new Project();
        project.setCode("cloud-ops");
        project.setName("云桥运维系统");
        project.setRootPath("D:/projects/云桥运维系统");

        FileAsset inScope = file("01需求/云桥运维系统需求规格说明书.docx");
        String inScopeText = "云桥运维系统需求规格说明书，覆盖告警聚合、巡检任务和监控看板。";
        FileAsset other = file("02资料/星河档案平台接口清单.xlsx");
        String otherText = "星河档案平台接口清单，包含档案检索、借阅审批、归档接口。";

        assertThat(service.evaluate(project, inScope, inScopeText).status())
                .isEqualTo(ProjectRelevanceService.IN_SCOPE);
        assertThat(service.evaluate(project, other, otherText).status())
                .isEqualTo(ProjectRelevanceService.OUT_OF_SCOPE);
    }

    private static Project project() {
        Project project = new Project();
        project.setCode("hnyzt");
        project.setName("湖南省地质一张图");
        project.setRootPath("D:/Java-p/Project-Knowledge-Center/10湖南数字地质项目省地质一张图");
        return project;
    }

    private static FileAsset file(String relPath) {
        FileAsset file = new FileAsset();
        file.setProjectId(1L);
        file.setRelPath(relPath);
        int slash = relPath.lastIndexOf('/');
        file.setName(slash >= 0 ? relPath.substring(slash + 1) : relPath);
        file.setCategory(FileCategoryResolver.DOC);
        file.setParseStatus("PARSED");
        return file;
    }
}
