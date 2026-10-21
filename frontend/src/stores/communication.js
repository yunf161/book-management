import { reactive } from "vue";
import { api } from "../api/library";
import { session } from "./session";

/** 全站复用一条连接；正文和未读数量通过HTTP查询，推送只负责触发同步。 */
export const communication = reactive({
  status: "disconnected",
  unreadTotal: 0,
  eventVersion: 0,
  syncVersion: 0,
});

let socket;
let restricted = false;
let generation = 0;
let retryTimer;
let heartbeat;
let syncTimer;
let fallbackTimer;
let attempts = 0;
let lastPong = 0;
let identity;
let unreadRequest = 0;
const seenEvents = new Set();

export async function refreshUnread() {
  const current = generation;
  const request = ++unreadRequest;
  if (
    restricted ||
    !session.token ||
    session.claims.memberType === "AI_LIBRARIAN"
  )
    return;
  try {
    const result = await api.messages(
      { box: "INBOX", page: 1, pageSize: 1 },
      { silent: true },
    );
    if (current === generation && request === unreadRequest)
      communication.unreadTotal = result.unreadTotal || 0;
  } catch {
    // 后台补查失败保留原数量，不打断当前页面；重连或手动刷新时再次补查。
  }
}

function scheduleUnread() {
  clearTimeout(syncTimer);
  syncTimer = setTimeout(refreshUnread, 250);
}

/** 退出或更换令牌时取消旧请求/定时器，避免旧连接重新建立或串用身份。 */
export function stopCommunication() {
  generation++;
  clearTimeout(retryTimer);
  clearTimeout(syncTimer);
  clearInterval(heartbeat);
  clearInterval(fallbackTimer);
  if (socket) {
    socket.onclose = null;
    socket.close();
    socket = undefined;
  }
  communication.status = "disconnected";
}

export function startCommunication(options = {}) {
  stopCommunication();
  restricted = !!options.restricted;
  if (identity !== session.user?.id) {
    seenEvents.clear();
    communication.unreadTotal = 0;
    identity = session.user?.id;
  }
  attempts = 0;
  if (!session.token || session.claims.memberType === "AI_LIBRARIAN") return;
  // 即使实时通道暂不可用，也先查未读；故障期间定期通过 HTTP 补查。
  refreshUnread();
  fallbackTimer = setInterval(() => {
    if (restricted || communication.status === "connected") return;
    if (
      typeof document !== "undefined" &&
      document.visibilityState !== "visible"
    )
      return;
    communication.syncVersion++;
    refreshUnread();
  }, 30000);
  connect(generation);
}

async function connect(current) {
  if (current !== generation || !session.token) return;
  communication.status = "connecting";
  try {
    // 每次重连领取新票据，旧票据不能复用；URL中不携带JWT。
    const ticket = await api.socketTicket();
    if (current !== generation) return;
    const url = new URL(`/api${ticket.path}`, window.location.origin);
    url.protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    url.searchParams.set("ticket", ticket.ticket);
    const connected = new WebSocket(url);
    socket = connected;
    connected.onopen = () => {
      if (current !== generation) return connected.close();
      communication.status = "connected";
      attempts = 0;
      lastPong = Date.now();
      connected.send("ping");
      clearInterval(heartbeat);
      heartbeat = setInterval(() => {
        if (Date.now() - lastPong > 60000) return connected.close();
        if (connected.readyState === WebSocket.OPEN) connected.send("ping");
      }, 25000);
      // 初次连接及每次重连都补查，不能认为所有离线消息都会再次推送。
      communication.syncVersion++;
      refreshUnread();
    };
    connected.onmessage = ({ data }) => {
      if (current !== generation) return;
      if (data === "pong") return void (lastPong = Date.now());
      try {
        const event = JSON.parse(data);
        if (event.eventType === "rules.updated") {
          window.dispatchEvent(new Event("library-rules-updated"));
          return;
        }
        if (restricted) return;
        if (
          !event.eventId ||
          event.eventType !== "message.created" ||
          String(event.recipientMemberId) !== String(session.user?.id)
        )
          return;
        if (seenEvents.has(event.eventId)) return;
        seenEvents.add(event.eventId);
        if (seenEvents.size > 500)
          seenEvents.delete(seenEvents.values().next().value);
        communication.eventVersion++;
        scheduleUnread();
      } catch {
        // 非业务载荷不进入页面数据。
      }
    };
    connected.onclose = () => {
      clearInterval(heartbeat);
      if (current === generation) retry(current);
    };
    connected.onerror = () => connected.close();
  } catch (error) {
    if (current !== generation) return;
    if ([401, 403].includes(error.response?.status)) {
      communication.status = "unavailable";
      return;
    }
    retry(current);
  }
}

function retry(current) {
  communication.status = "reconnecting";
  const delay = Math.min(30000, 1000 * 2 ** Math.min(attempts++, 5));
  retryTimer = setTimeout(() => connect(current), delay + Math.random() * 500);
}

export function resumeCommunication(force = false) {
  if (
    restricted ||
    !session.token ||
    session.claims.memberType === "AI_LIBRARIAN"
  )
    return;
  if (communication.status === "connecting") return;
  if (socket?.readyState === WebSocket.OPEN) {
    communication.syncVersion++;
    refreshUnread();
  } else if (force || communication.status !== "unavailable") {
    clearTimeout(retryTimer);
    connect(generation);
  }
}
