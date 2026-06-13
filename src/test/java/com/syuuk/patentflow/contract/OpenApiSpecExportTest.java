package com.syuuk.patentflow.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CONTRACT-10(2단계): 빌드 시점 OpenAPI 스펙을 단일 소스로 박제하는 드리프트 게이트.
 *
 * <p>springdoc이 컨트롤러/DTO에서 실시간 생성하는 {@code /v3/api-docs}를 떠서 키 정렬·LF·2-space로
 * 정규화한 뒤 모듈 루트의 {@code openapi.json}과 비교한다. 컨트롤러 시그니처나 DTO 필드가 바뀌면 이
 * 테스트가 깨져, 커밋된 스펙(=FE 코드젠 입력)이 BE 실제 계약과 어긋나는 것을 빌드에서 차단한다.
 *
 * <p>스펙을 (재)생성하려면: {@code mvn -Dtest=OpenApiSpecExportTest -Dopenapi.update=true test}
 * 그 후 변경된 {@code openapi.json}을 커밋한다. 일반 {@code mvn test}에서는 비교만 수행한다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiSpecExportTest {

    /** 모듈 루트(=surefire 작업 디렉터리) 기준 커밋 경로. FE 코드젠이 동기화해 가져가는 단일 소스. */
    private static final Path SPEC_PATH = Path.of("openapi.json");

    @Autowired
    private MockMvc mockMvc;

    /** 키 정렬로 결정적 직렬화. ObjectNode 필드가 알파벳순으로 출력돼 OS/실행마다 동일한 바이트가 된다. */
    private final ObjectMapper canonicalMapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Test
    void exportedOpenApiSpecMatchesCommittedSnapshot() throws Exception {
        String rawJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String canonical = canonicalize(rawJson);

        boolean update = Boolean.getBoolean("openapi.update");
        if (update || !Files.exists(SPEC_PATH)) {
            Files.writeString(SPEC_PATH, canonical, StandardCharsets.UTF_8);
            // 생성/갱신 모드: 스펙을 기록하고 통과한다. 변경분을 커밋해야 한다.
            assertThat(Files.readString(SPEC_PATH, StandardCharsets.UTF_8)).isEqualTo(canonical);
            return;
        }

        String committed = Files.readString(SPEC_PATH, StandardCharsets.UTF_8);
        assertThat(committed)
                .as("openapi.json 이 BE 실제 계약과 어긋남 — `mvn -Dtest=OpenApiSpecExportTest "
                        + "-Dopenapi.update=true test` 로 재생성 후 커밋하세요(FE 코드젠 입력 단일 소스).")
                .isEqualTo(canonical);
    }

    /** 원본 JSON을 트리로 읽어 키 정렬 + 2-space + LF 로 재직렬화한다(끝에 개행 1줄 보장). */
    private String canonicalize(String rawJson) throws Exception {
        // ORDER_MAP_ENTRIES_BY_KEYS는 Map 직렬화에만 적용되고 ObjectNode 필드는 정렬하지 않는다.
        // readTree(JsonNode)로 받으면 springdoc의 리플렉션 순서 비결정성이 그대로 노출돼
        // 같은 코드에서도 실행마다 스냅샷이 어긋난다 — Map 트리로 읽어 전 레벨 키 정렬을 보장한다.
        Object tree = canonicalMapper.readValue(rawJson, Object.class);
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return canonicalMapper.writer(printer).writeValueAsString(tree) + "\n";
    }
}
