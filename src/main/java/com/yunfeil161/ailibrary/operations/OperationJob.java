package com.yunfeil161.ailibrary.operations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Data;

/** 作业列表只暴露元信息；文件通过独立鉴权下载，不在列表返回二进制。 */
@Data
public class OperationJob {

  private Long id;
  private String jobId;
  private Long memberId;
  private String type;
  private String state;
  private String requestHash;

  @JsonIgnore
  private byte[] inputData;

  private Integer totalRows;
  private Integer completedRows;
  private Integer successRows;
  private Integer failedRows;
  private String resultJson;
  private String errorCode;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
