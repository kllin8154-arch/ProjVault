package com.projvault.pkc.file;

import com.projvault.ai.ExtractModelProvider;
import com.projvault.pkc.scan.ScanCancellation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigExtractServiceTest {

    private final ConfigExtractService service = new ConfigExtractService(
            (docName, context) -> List.of(),
            new ScanCancellation());

    @Test
    void filtersExampleConfigValuesByContext() {
        String text = """
                API 示例，请按需修改：
                jdbc:mysql://192.168.1.1:3306/demo
                示例 IP：10.0.0.8:8080
                """;

        List<ConfigItem> items = service.extractFromText(text, 1L, 2L, 3L);

        assertThat(items).isEmpty();
    }

    @Test
    void filtersValuesInsideApiExampleCodeBlock() {
        String text = """
                ## API 示例

                ```yaml
                datasource: jdbc:mysql://192.168.1.1:3306/demo
                callback: http://10.0.0.8:8080/mock/callback
                server: example-host:8080
                ```

                ## 正式部署信息

                正式部署数据库地址：jdbc:mysql://192.168.30.11:3306/prod
                生产服务地址 server=db.prod.local:5432
                """;

        List<ConfigItem> items = service.extractFromText(text, 1L, 2L, 3L);

        assertThat(items)
                .extracting(ConfigItem::getKeyValue)
                .contains("jdbc:mysql://192.168.30.11:3306/prod", "db.prod.local:5432")
                .doesNotContain("jdbc:mysql://192.168.1.1:3306/demo",
                        "192.168.1.1:3306",
                        "10.0.0.8:8080",
                        "http://10.0.0.8:8080/mock/callback",
                        "example-host:8080");
        assertThat(items)
                .extracting(ConfigItem::getContext)
                .allSatisfy(context -> assertThat(context)
                        .doesNotContain("10.0.0.8", "example-host", "/mock/callback"));
    }

    @Test
    void keepsRealDeploymentConfigValues() {
        String text = """
                正式部署数据库地址：jdbc:mysql://192.168.30.11:3306/prod
                生产服务地址 server=db.prod.local:5432
                """;

        List<ConfigItem> items = service.extractFromText(text, 1L, 2L, 3L);

        assertThat(items)
                .extracting(ConfigItem::getKeyValue)
                .contains("jdbc:mysql://192.168.30.11:3306/prod", "db.prod.local:5432");
    }

    @Test
    void filtersIpValuesFromFieldDescriptionExamples() {
        String text = """
                IP白名单 text - IP白名单 允许访问的IP列表（JSON数组格式，如：["192.168.1.1","10.0.0.0/24"]）
                是否启用鉴权 tinyint 1 是否启用鉴权
                """;

        List<ConfigItem> items = service.extractFromText(text, 1L, 2L, 3L);

        assertThat(items)
                .extracting(ConfigItem::getKeyValue)
                .doesNotContain("192.168.1.1", "10.0.0.0");
    }

    @Test
    void extractsFormalValuesFromMixedReferenceAsset() {
        FileAsset asset = new FileAsset();
        asset.setRelevanceStatus(ProjectRelevanceService.REFERENCE);
        String text = """
                API \u793a\u4f8b\uff1ajdbc:mysql://192.168.1.1:3306/demo
                \u6b63\u5f0f\u90e8\u7f72\u6570\u636e\u5e93\u5730\u5740\uff1ajdbc:mysql://192.168.30.11:3306/prod
                """;

        assertThat(service.shouldExtract(asset, text)).isTrue();
    }
}
