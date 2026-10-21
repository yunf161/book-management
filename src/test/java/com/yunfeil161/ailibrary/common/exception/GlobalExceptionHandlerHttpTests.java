package com.yunfeil161.ailibrary.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 验证常见HTTP协议错误不会落入通用500处理。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerHttpTests {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void unsupportedContentTypeReturns415() throws Exception {
    mockMvc
      .perform(post("/login").contentType(MediaType.TEXT_PLAIN).content("{}"))
      .andExpect(status().isUnsupportedMediaType())
      .andExpect(jsonPath("$.code").value(415));
  }

  @Test
  void missingRequiredParameterReturns400() throws Exception {
    mockMvc
      .perform(delete("/reading-plans"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  void unsupportedMethodReturns405() throws Exception {
    mockMvc
      .perform(get("/login"))
      .andExpect(status().isMethodNotAllowed())
      .andExpect(jsonPath("$.code").value(405));
  }

  @Test
  void unknownPathReturns404Json() throws Exception {
    mockMvc
      .perform(get("/path-that-does-not-exist"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(404));
  }
}
