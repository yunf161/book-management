import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { SourceTextModule, SyntheticModule, createContext } from "node:vm";

const source = await readFile(
  new URL("../src/stores/communication.js", import.meta.url),
  "utf8",
);

/** 替换网络和时钟，直接执行实际连接管理代码，覆盖重连及身份切换竞态。 */
async function harness(overrides = {}) {
  const sockets = [],
    timers = new Map(),
    intervals = new Map();
  let timerId = 0,
    tickets = 0,
    queries = 0;
  const session = {
    token: "test-token",
    user: { id: 10 },
    claims: { memberType: "READER" },
  };
  const api = {
    socketTicket: async () => ({
      ticket: `ticket-${++tickets}`,
      path: "/ws/events",
    }),
    messages: async () => {
      queries++;
      return { unreadTotal: 3 };
    },
    ...overrides,
  };
  class Socket {
    static OPEN = 1;
    constructor(url) {
      this.url = String(url);
      this.readyState = 0;
      this.sent = [];
      sockets.push(this);
    }
    send(value) {
      this.sent.push(value);
    }
    open() {
      this.readyState = 1;
      this.onopen?.();
    }
    receive(event) {
      this.onmessage?.({
        data: typeof event === "string" ? event : JSON.stringify(event),
      });
    }
    close() {
      this.readyState = 3;
      this.onclose?.();
    }
  }
  const context = createContext({
    URL,
    Date,
    console,
    WebSocket: Socket,
    window: {
      location: { origin: "https://library.test", protocol: "https:" },
    },
    setTimeout: (fn, delay) => {
      timers.set(++timerId, { fn, delay });
      return timerId;
    },
    clearTimeout: (id) => timers.delete(id),
    setInterval: (fn, delay) => {
      intervals.set(++timerId, { fn, delay });
      return timerId;
    },
    clearInterval: (id) => intervals.delete(id),
  });
  const dependencies = {
    vue: { reactive: (object) => object },
    "../api/library": { api },
    "./session": { session },
  };
  const module = new SourceTextModule(source, { context });
  await module.link((specifier) => {
    const exports = dependencies[specifier];
    return new SyntheticModule(
      Object.keys(exports),
      function () {
        for (const [key, value] of Object.entries(exports))
          this.setExport(key, value);
      },
      { context },
    );
  });
  await module.evaluate();
  const flush = async () => {
    for (let i = 0; i < 5; i++) await Promise.resolve();
  };
  return {
    state: module.namespace,
    session,
    sockets,
    timers,
    intervals,
    flush,
    get queries() {
      return queries;
    },
    get tickets() {
      return tickets;
    },
  };
}

test("一次性票据连接、心跳及初次HTTP补查", async () => {
  const h = await harness();
  h.state.startCommunication();
  await h.flush();
  assert.equal(h.sockets.length, 1);
  assert.equal(
    h.sockets[0].url,
    "wss://library.test/api/ws/events?ticket=ticket-1",
  );
  assert.ok(!h.sockets[0].url.includes(h.session.token));
  h.sockets[0].open();
  await h.flush();
  assert.equal(h.state.communication.status, "connected");
  assert.equal(h.state.communication.unreadTotal, 3);
  assert.deepEqual(h.sockets[0].sent, ["ping"]);
});

test("提醒按eventId去重，其他成员事件不会触发同步", async () => {
  const h = await harness();
  h.state.startCommunication();
  await h.flush();
  h.sockets[0].open();
  const event = {
    eventId: "e1",
    eventType: "message.created",
    recipientMemberId: 10,
  };
  h.sockets[0].receive(event);
  h.sockets[0].receive(event);
  h.sockets[0].receive({ ...event, eventId: "e2", recipientMemberId: 20 });
  assert.equal(h.state.communication.eventVersion, 1);
  assert.equal(h.timers.size, 1);
});

test("断线退避重连重新领取票据并补查离线消息", async () => {
  const h = await harness();
  h.state.startCommunication();
  await h.flush();
  h.sockets[0].open();
  h.sockets[0].close();
  assert.equal(h.state.communication.status, "reconnecting");
  const timer = [...h.timers.values()][0];
  assert.ok(timer.delay >= 1000 && timer.delay < 1500);
  timer.fn();
  await h.flush();
  h.sockets[1].open();
  await h.flush();
  assert.equal(h.tickets, 2);
  assert.equal(h.queries, 3);
  assert.ok(h.sockets[1].url.includes("ticket-2"));
});

test("退出取消连接、心跳和重连任务", async () => {
  const h = await harness();
  h.state.startCommunication();
  await h.flush();
  h.sockets[0].open();
  h.state.stopCommunication();
  assert.equal(h.sockets[0].readyState, 3);
  assert.equal(h.timers.size, 0);
  assert.equal(h.intervals.size, 0);
  assert.equal(h.state.communication.status, "disconnected");
});

test("身份切换后迟到的旧HTTP响应不能覆盖新身份数据", async () => {
  const pendingRequests = [];
  const h = await harness({
    messages: () =>
      new Promise((r) => {
        pendingRequests.push(r);
      }),
  });
  const pending = h.state.refreshUnread();
  h.session.user = { id: 20 };
  h.state.startCommunication();
  await h.flush();
  pendingRequests[0]({ unreadTotal: 99 });
  await pending;
  assert.equal(h.state.communication.unreadTotal, 0);
});

test("没有WebSocket权限时停止重试，保留HTTP业务能力", async () => {
  const h = await harness({
    socketTicket: async () => {
      throw { response: { status: 403 } };
    },
  });
  h.state.startCommunication();
  await h.flush();
  assert.equal(h.state.communication.status, "unavailable");
  assert.equal(h.timers.size, 0);
  await h.state.refreshUnread();
  assert.equal(h.state.communication.unreadTotal, 3);
});

test("同一身份的旧未读查询不能覆盖更新的未读数量", async () => {
  const pending = [];
  const h = await harness({
    messages: () => new Promise((resolve) => pending.push(resolve)),
  });
  const older = h.state.refreshUnread();
  const newer = h.state.refreshUnread();
  pending[1]({ unreadTotal: 0 });
  await newer;
  pending[0]({ unreadTotal: 7 });
  await older;
  assert.equal(h.state.communication.unreadTotal, 0);
});

test("权限恢复后手动刷新可以重新领取连接票据", async () => {
  let allowed = false;
  const h = await harness({
    socketTicket: async () => {
      if (!allowed) throw { response: { status: 403 } };
      return { ticket: "new-ticket", path: "/ws/events" };
    },
  });
  h.state.startCommunication();
  await h.flush();
  assert.equal(h.state.communication.status, "unavailable");
  allowed = true;
  h.state.resumeCommunication(true);
  await h.flush();
  assert.equal(h.sockets.length, 1);
});

test("AI身份不建立通信连接，页面回到前台会补查人类用户消息", async () => {
  const h = await harness();
  h.session.claims.memberType = "AI_LIBRARIAN";
  h.state.startCommunication();
  await h.flush();
  assert.equal(h.sockets.length, 0);
  h.state.resumeCommunication();
  await h.flush();
  assert.equal(h.sockets.length, 0);
  h.session.claims.memberType = "READER";
  h.state.startCommunication();
  await h.flush();
  h.sockets[0].open();
  const before = h.state.communication.syncVersion;
  h.state.resumeCommunication();
  await h.flush();
  assert.equal(h.state.communication.syncVersion, before + 1);
});

test("实时通道不可用仍通过HTTP持续补查未读消息", async () => {
  const h = await harness({
    socketTicket: async () => {
      throw { response: { status: 403 } };
    },
  });
  h.state.startCommunication();
  await h.flush();
  assert.equal(h.state.communication.unreadTotal, 3);
  assert.equal(h.queries, 1);
  const fallback = [...h.intervals.values()].find(
    (timer) => timer.delay === 30000,
  );
  assert.ok(fallback);
  fallback.fn();
  await h.flush();
  assert.equal(h.queries, 2);
  h.state.stopCommunication();
  assert.equal(h.intervals.size, 0);
});

// Restricted sessions retain only the lightweight policy channel.
test("restricted socket never queries unread business data", async () => {
  const h = await harness();
  h.state.startCommunication({ restricted: true });
  await h.flush();
  assert.equal(h.sockets.length, 1);
  h.sockets[0].open();
  h.sockets[0].receive({
    eventId: "old-message",
    eventType: "message.created",
    recipientMemberId: 10,
  });
  for (const { fn } of h.intervals.values()) fn();
  await h.flush();
  assert.equal(h.queries, 0);
  h.state.stopCommunication();
});
