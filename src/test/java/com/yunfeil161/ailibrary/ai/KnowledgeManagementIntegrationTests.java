package com.yunfeil161.ailibrary.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.ai.knowledge.KnowledgeGateway;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 真实角色授权及Controller契约；向量服务独立进行真实HTTP和存储测试。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:knowledge_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeManagementIntegrationTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  KnowledgeGateway gateway;

  void login(String code) {
    Long role = jdbc.queryForObject(
      "select id from role where code=?",
      Long.class,
      code
    );
    LoginUserContext.set(
      new LoginSession(
        "test",
        123L,
        role,
        MemberType.EMPLOYEE,
        code,
        "test@example.test",
        "测试"
      )
    );
  }

  @BeforeEach
  void setup() {
    login("ADMIN");
    when(gateway.request(anyString(), anyString(), any())).thenReturn(
      Map.of("total", 0, "rows", List.of())
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  @Test
  void queryCombinesFields() throws Exception {
    mvc
      .perform(
        get("/admin/knowledge/documents")
          .param("title", "借阅")
          .param("kind", "general")
          .param("page", "2")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(0));
    verify(gateway).request(eq("GET"), contains("title="), isNull());
  }

  @Test
  void allManagementEndpointsWork() throws Exception {
    mvc.perform(get("/admin/knowledge/status")).andExpect(status().isOk());
    mvc
      .perform(get("/admin/knowledge/jobs/abc-123"))
      .andExpect(status().isOk());
    mvc
      .perform(get("/admin/knowledge/documents/manual"))
      .andExpect(status().isOk());
    for (String path : List.of("documents", "rebuild", "search"))
      mvc
        .perform(
          post("/admin/knowledge/" + path)
            .contentType("application/json")
            .content("{}")
        )
        .andExpect(status().isOk());
    mvc
      .perform(
        put("/admin/knowledge/documents/manual")
          .contentType("application/json")
          .content("{}")
      )
      .andExpect(status().isOk());
  }

  @Test
  void readerAndLibrarianCannotManage() throws Exception {
    for (String code : List.of("READER", "LIBRARIAN")) {
      login(code);
      mvc
        .perform(get("/admin/knowledge/status"))
        .andExpect(status().isForbidden());
      mvc
        .perform(
          post("/admin/knowledge/rebuild")
            .contentType("application/json")
            .content("{}")
        )
        .andExpect(status().isForbidden());
    }
    verifyNoInteractions(gateway);
  }

  @Test
  void unauthenticatedDenied() throws Exception {
    LoginUserContext.clear();
    mvc
      .perform(get("/admin/knowledge/status"))
      .andExpect(status().isUnauthorized());
    verifyNoInteractions(gateway);
  }

  @Test
  void malformedFilterAndPathDenied() throws Exception {
    mvc
      .perform(get("/admin/knowledge/documents").param("file", "secret"))
      .andExpect(status().isBadRequest());
    mvc
      .perform(get("/admin/knowledge/jobs/bad.id"))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(gateway);
  }

  @Test
  void filePreviewUsesExistingManagePermissionAndPassesOnlyBytesAndName()
    throws Exception {
    for (String extension : List.of("md", "txt", "csv", "json", "pdf")) {
      mvc
        .perform(
          multipart("/admin/knowledge/import-preview").file(
            new MockMultipartFile(
              "file",
              "book." + extension,
              "application/octet-stream",
              "sample".getBytes()
            )
          )
        )
        .andExpect(status().isOk());
    }
    verify(gateway, times(5)).request(
      eq("POST"),
      eq("import-preview"),
      argThat(
        body ->
          body instanceof Map<?, ?> values &&
          values.size() == 2 &&
          values.containsKey("fileName") &&
          "c2FtcGxl".equals(values.get("contentBase64"))
      )
    );
  }

  @Test
  void invalidFileTypePathEmptyAndOversizedContentAreRejectedBeforeGateway()
    throws Exception {
    for (String name : List.of("bad.docx", "../bad.pdf", "C:\\bad.pdf")) {
      mvc
        .perform(
          multipart("/admin/knowledge/import-preview").file(
            new MockMultipartFile(
              "file",
              name,
              "application/octet-stream",
              new byte[] { 1 }
            )
          )
        )
        .andExpect(status().isBadRequest());
    }
    mvc
      .perform(
        multipart("/admin/knowledge/import-preview").file(
          new MockMultipartFile(
            "file",
            "empty.json",
            "application/json",
            new byte[0]
          )
        )
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        multipart("/admin/knowledge/import-preview").file(
          new MockMultipartFile(
            "file",
            "large.csv",
            "text/csv",
            new byte[600001]
          )
        )
      )
      .andExpect(status().isBadRequest());
    verifyNoInteractions(gateway);
  }

  @Test
  void readerLibrarianAndAnonymousCannotParseFiles() throws Exception {
    for (String code : List.of("READER", "LIBRARIAN")) {
      login(code);
      mvc
        .perform(
          multipart("/admin/knowledge/import-preview").file(
            new MockMultipartFile(
              "file",
              "book.pdf",
              "application/pdf",
              new byte[] { 1 }
            )
          )
        )
        .andExpect(status().isForbidden());
    }
    LoginUserContext.clear();
    mvc
      .perform(
        multipart("/admin/knowledge/import-preview").file(
          new MockMultipartFile("file", "book.csv", "text/csv", new byte[] {
            1,
          })
        )
      )
      .andExpect(status().isUnauthorized());
    verifyNoInteractions(gateway);
  }
}
