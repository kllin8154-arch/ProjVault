package com.projvault.pkc.artifact;

public enum ArtifactType {
    PROJECT_REPORT("项目报告", "围绕项目范围、当前进展、交付成果、变更、风险、待办和决策建议形成项目经理报告。"),
    DESIGN_SPEC("设计说明", "形成包含背景、目标、范围、总体设计、模块设计、接口、数据、安全、部署和验收的设计说明。"),
    DATABASE_DESIGN("数据库设计", "根据项目资料形成可执行或可审查的数据库设计，包含表、字段、约束、索引、关系和必要注释。"),
    PRESENTATION("汇报演示", "形成适合项目汇报的演示结构，突出目标、范围、阶段成果、关键数据、风险、计划和决策事项。"),
    CUSTOM_DOCUMENT("自定义文档", "严格依据用户要求和项目证据编写可审查的项目文档。 ");

    private final String label;
    private final String guidance;

    ArtifactType(String label, String guidance) {
        this.label = label;
        this.guidance = guidance;
    }

    public String getLabel() { return label; }
    public String getGuidance() { return guidance; }
}
