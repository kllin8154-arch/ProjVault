package com.projvault.pkc.rag;

import com.projvault.ai.RagAnswer;
import com.projvault.pkc.file.FileAsset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceCitationTest {

    @Test
    void recognizesFileExplicitlyNamedByAnswer() {
        FileAsset file = new FileAsset();
        file.setName("省地质一张图多做额外功能清单.xlsx");
        file.setRelPath("04项目执行阶段/省地质一张图多做额外功能清单.xlsx");

        assertThat(RagService.answerNamesFile(
                "来源：《省地质一张图多做额外功能清单.xlsx》",
                file)).isTrue();
        assertThat(RagService.answerNamesFile("来源：其他文件.xlsx", file)).isFalse();
    }

    @Test
    void distinguishesProviderFailureFromRealNoAnswer() {
        assertThat(RagService.isProviderFailure(new RagAnswer("", List.of(), true))).isTrue();
        assertThat(RagService.isProviderFailure(
                new RagAnswer("模型调用失败：timeout", List.of(), false))).isTrue();
        assertThat(RagService.isProviderFailure(
                new RagAnswer("资料中未找到相关内容。", List.of(), false))).isFalse();
        assertThat(RagService.isProviderFailure(
                new RagAnswer("根据资料可以确认合同金额为86万元。", List.of(), true))).isFalse();
        assertThat(RagService.isRetryableProviderFailure(
                new RagAnswer("模型调用失败：timeout", List.of(), false))).isTrue();
        assertThat(RagService.isRetryableProviderFailure(
                new RagAnswer("", List.of(), true))).isTrue();
    }

    @Test
    void retriesBlankModelResponseOnce() {
        AtomicInteger calls = new AtomicInteger();
        RagService service = new RagService(
                null,
                (question, contexts) -> calls.incrementAndGet() == 1
                        ? new RagAnswer("", List.of(), true)
                        : new RagAnswer("第二次调用成功。", List.of(), true),
                null,
                null,
                null,
                null);

        RagAnswer answer = service.requestModelAnswer("问题", List.of("证据"));

        assertThat(calls).hasValue(2);
        assertThat(answer.answer()).isEqualTo("第二次调用成功。");
        assertThat(answer.grounded()).isTrue();
    }

    @Test
    void returnsExplicitDegradedMessageAfterRepeatedFailure() {
        AtomicInteger calls = new AtomicInteger();
        RagService service = new RagService(
                null,
                (question, contexts) -> {
                    calls.incrementAndGet();
                    return new RagAnswer("", List.of(), true);
                },
                null,
                null,
                null,
                null);

        RagAnswer answer = service.requestModelAnswer("问题", List.of("证据"));

        assertThat(calls).hasValue(2);
        assertThat(answer.answer()).contains("回答模型暂时不可用");
        assertThat(answer.grounded()).isFalse();
    }

    @Test
    void retriesTransientProviderErrorOnce() {
        AtomicInteger calls = new AtomicInteger();
        RagService service = new RagService(
                null,
                (question, contexts) -> {
                    calls.incrementAndGet();
                    return new RagAnswer("模型调用失败：timeout", List.of(), false);
                },
                null,
                null,
                null,
                null);

        RagAnswer answer = service.requestModelAnswer("问题", List.of("证据"));

        assertThat(calls).hasValue(2);
        assertThat(answer.answer()).contains("回答模型暂时不可用");
        assertThat(answer.grounded()).isFalse();
    }
}
