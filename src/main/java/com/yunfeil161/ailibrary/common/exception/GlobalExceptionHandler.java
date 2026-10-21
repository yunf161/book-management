package com.yunfeil161.ailibrary.common.exception;

import com.yunfeil161.ailibrary.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一将业务异常转换为 Result 响应，并设置正确的 HTTP 状态码。
 * 未识别异常会在此处记录完整日志，避免向客户端泄露内部实现细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 将非法枚举、错误日期格式和路径参数类型错误统一转换为HTTP 400。 */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
  })
  public ResponseEntity<Result> handleRequestBindingException(Exception e) {
    log.warn("请求参数类型或JSON格式错误：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      Result.error(HttpStatus.BAD_REQUEST.value(), "请求参数类型或格式错误")
    );
  }

  /** 处理缺少必填查询参数或表单参数的请求。 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Result> handleMissingRequestParameter(
    MissingServletRequestParameterException e
  ) {
    log.warn("缺少请求参数：{}", e.getParameterName());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      Result.error(
        HttpStatus.BAD_REQUEST.value(),
        "缺少必填参数：" + e.getParameterName()
      )
    );
  }

  /** 请求体媒体类型不受支持时返回HTTP 415，而不是通用500。 */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Result> handleUnsupportedMediaType(
    HttpMediaTypeNotSupportedException e
  ) {
    log.warn("不支持的请求媒体类型：{}", e.getContentType());
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
      Result.error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
        "请求Content-Type不受支持"
      )
    );
  }

  /** URL存在但HTTP方法不匹配时返回HTTP 405。 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Result> handleMethodNotSupported(
    HttpRequestMethodNotSupportedException e
  ) {
    log.warn("不支持的HTTP方法：{}", e.getMethod());
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
      Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不受支持")
    );
  }

  /** 未匹配Controller或静态资源的路径返回统一JSON 404。 */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Result> handleNoResourceFound(
    NoResourceFoundException e
  ) {
    log.warn("请求路径不存在：{}", e.getResourcePath());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      Result.error(HttpStatus.NOT_FOUND.value(), "请求路径不存在")
    );
  }

  /** 处理未登录或认证凭证失效的请求。 */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Result> handleUnauthorizedException(
    UnauthorizedException e
  ) {
    log.warn("认证失败：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
      Result.error(HttpStatus.UNAUTHORIZED.value(), e.getMessage())
    );
  }

  /** 处理请求格式、字段类型或必填字段错误。 */
  @ExceptionHandler(WrongFormatException.class)
  public ResponseEntity<Result> handleWrongFormatException(
    WrongFormatException e
  ) {
    log.warn("格式错误：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
      Result.error(HttpStatus.BAD_REQUEST.value(), e.getMessage())
    );
  }

  /** 处理已认证但无权执行操作的请求。 */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Result> handleForbiddenException(ForbiddenException e) {
    log.warn("权限不足：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
      Result.error(HttpStatus.FORBIDDEN.value(), e.getMessage())
    );
  }

  /** 处理资源不存在或对当前主体不可见的请求。 */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Result> handleNotFoundException(NotFoundException e) {
    log.warn("资源不存在：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      Result.error(HttpStatus.NOT_FOUND.value(), e.getMessage())
    );
  }

  /**
   * 处理业务状态冲突和数据库唯一键冲突。
   * 数据库异常不返回底层 SQL 信息，避免暴露表和约束细节。
   */
  @ExceptionHandler({ ConflictException.class, DuplicateKeyException.class })
  public ResponseEntity<Result> handleConflictException(RuntimeException e) {
    // 统一数据库唯一键冲突的客户端提示，其他冲突保留业务层传入的信息。
    String message =
      e instanceof DuplicateKeyException
        ? "数据已存在，请勿重复提交"
        : e.getMessage();
    log.warn("状态冲突：{}", message);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
      Result.error(HttpStatus.CONFLICT.value(), message)
    );
  }

  /** 处理请求合法但不满足借阅、预约等业务条件的情况。 */
  @ExceptionHandler(UnprocessableEntityException.class)
  public ResponseEntity<Result> handleBusinessException(
    UnprocessableEntityException e
  ) {
    log.warn("业务处理失败：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
      Result.error(HttpStatus.UNPROCESSABLE_CONTENT.value(), e.getMessage())
    );
  }

  /** 处理验证码等受限操作超过频率限制的情况。 */
  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<Result> handleTooManyRequestsException(
    TooManyRequestsException e
  ) {
    log.warn("请求过于频繁：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
      Result.error(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage())
    );
  }

  /** 处理模型、邮件等同步依赖暂时不可用的情况。 */
  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<Result> handleServiceUnavailableException(
    ServiceUnavailableException e
  ) {
    log.error("依赖服务不可用：{}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
      Result.error(HttpStatus.SERVICE_UNAVAILABLE.value(), e.getMessage())
    );
  }

  /**
   * 兜底处理程序缺陷和未识别内部异常。
   * 日志记录完整异常堆栈，客户端仅获得通用提示。
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result> handleException(Exception e) {
    log.error("系统异常", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
      Result.error(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "系统繁忙，请稍后重试"
      )
    );
  }
}
