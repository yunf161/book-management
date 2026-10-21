package com.yunfeil161.ailibrary.book.cover.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.common.exception.GlobalExceptionHandler;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** multipart 边界错误继续使用项目统一响应格式。 */
@Order(-1)
// 超过传输上限可能在 Controller 匹配前发生，因此不能仅按 Controller 类型筛选。
@RestControllerAdvice
@RequiredArgsConstructor
public class BookCoverExceptionHandler {

  private final GlobalExceptionHandler handler;

  @ExceptionHandler({
    MaxUploadSizeExceededException.class,
    MissingServletRequestPartException.class,
  })
  public ResponseEntity<Result> invalidUpload(Exception failure) {
    return handler.handleWrongFormatException(
      new WrongFormatException("封面文件缺失或超过上传大小限制")
    );
  }
}
