package com.yunfeil161.ailibrary.common.util;

import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** 在接收过程中限制HTTP响应字节数，而非完整分配内存后才检查长度。 */
public final class BoundedBodyHandler
  implements HttpResponse.BodyHandler<byte[]>
{

  private final int maximumBytes;

  public BoundedBodyHandler(int maximumBytes) {
    if (maximumBytes < 1) throw new IllegalArgumentException(
      "响应上限必须大于0"
    );
    this.maximumBytes = maximumBytes;
  }

  @Override
  public HttpResponse.BodySubscriber<byte[]> apply(
    HttpResponse.ResponseInfo info
  ) {
    var delegate = HttpResponse.BodySubscribers.ofByteArray();
    return new HttpResponse.BodySubscriber<>() {
      private Flow.Subscription subscription;
      private int received;
      private boolean failed;

      @Override
      public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
      }

      @Override
      public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        delegate.onSubscribe(value);
      }

      @Override
      public void onNext(List<ByteBuffer> buffers) {
        if (failed) return;
        for (var buffer : buffers) {
          if (buffer.remaining() > maximumBytes - received) {
            failed = true;
            subscription.cancel();
            delegate.onError(new IOException("HTTP响应超过字节上限"));
            return;
          }
          received += buffer.remaining();
        }
        delegate.onNext(buffers);
      }

      @Override
      public void onError(Throwable failure) {
        if (!failed) {
          failed = true;
          delegate.onError(failure);
        }
      }

      @Override
      public void onComplete() {
        if (!failed) delegate.onComplete();
      }
    };
  }
}
