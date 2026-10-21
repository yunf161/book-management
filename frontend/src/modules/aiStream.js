/** 有界SSE解析：分块与UTF-8字符可以跨网络包；只有done才表示回答有效。 */
export async function readAiEvents(response, onEvent) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = "",
    done = false,
    total = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      // 先合并再规范化，避免CR和LF跨网络包时丢失事件边界。
      pending = (
        pending + decoder.decode(chunk.value, { stream: !chunk.done })
      ).replace(/\r\n/g, "\n");
      total += chunk.value?.byteLength || 0;
      if (total > 200000 || pending.length > 100000)
        throw new Error("AI响应超出长度限制");
      let boundary;
      while ((boundary = pending.indexOf("\n\n")) >= 0) {
        const frame = pending.slice(0, boundary);
        pending = pending.slice(boundary + 2);
        const event = frame
          .split("\n")
          .find((line) => line.startsWith("event: "))
          ?.slice(7);
        const data = frame
          .split("\n")
          .filter((line) => line.startsWith("data: "))
          .map((line) => line.slice(6))
          .join("\n");
        if (!event || !data) continue;
        if (
          ![
            "meta",
            "delta",
            "sources",
            "done",
            "error",
            "action",
            "usage",
          ].includes(event)
        )
          throw new Error("AI事件格式错误");
        const value = JSON.parse(data);
        if (!value || typeof value !== "object" || Array.isArray(value))
          throw new Error("AI事件内容格式错误");
        if (event === "delta" && typeof value.text !== "string")
          throw new Error("AI回答片段格式错误");
        if (event === "sources" && !Array.isArray(value.sources))
          throw new Error("AI来源格式错误");
        if (event === "usage" && !Array.isArray(value.calls))
          throw new Error("AI用量格式错误");
        if (
          event === "action" &&
          (!value.actionId ||
            !value.requestHash ||
            !value.skill ||
            value.state !== "PREPARED")
        )
          throw new Error("AI操作草稿格式错误");
        if (event === "error") {
          const error = new Error(value.message || "AI回答失败");
          error.status = value.code;
          throw error;
        }
        onEvent(event, value);
        if (event === "done") {
          done = true;
          return;
        }
      }
      if (chunk.done) break;
    }
    if (!done) throw new Error("AI回答提前中断，本轮内容未确认");
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
