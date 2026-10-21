import http from "./http";

const clean = (params = {}) =>
  Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== "" && value !== null && value !== undefined,
    ),
  );

export const api = {
  operationLogs: (params) =>
    http.get("/admin/operation-logs", { params: clean(params) }),
  // 只查询当前推荐；刷新键在一次用户操作内固定，网络重试沿用同一键。
  interestTags: (params) =>
    http.get("/interest-tags", { params: clean(params) }),
  myInterests: () => http.get("/me/interests"),
  updateInterests: (tagIds) => http.put("/me/interests", { tagIds }),
  currentRecommendations: () => http.get("/me/recommendations"),
  refreshRecommendations: (requestId) =>
    http.post("/me/recommendations/refresh", null, {
      headers: { "Idempotency-Key": requestId },
      // 覆盖普通请求12秒超时，为后端30秒重排及规则降级留出时间。
      timeout: 35000,
    }),
  recommendationFeedback: (itemId, kind) =>
    http.post(`/me/recommendations/${itemId}/feedback`, { kind }),
  updateBookTags: (bookId, tagIds, expectedVersion) =>
    http.put(`/admin/books/${bookId}/tags`, { tagIds, expectedVersion }),
  // 封面使用 multipart，不把图片内容或客户端路径写入书目JSON。
  uploadBookCover: (id, file, expectedVersion, replace = false) => {
    const body = new FormData();
    body.append("file", file);
    body.append("expectedVersion", expectedVersion);
    return replace
      ? http.put(`/admin/books/${id}/cover`, body, { timeout: 120000 })
      : http.post(`/admin/books/${id}/cover`, body, { timeout: 120000 });
  },
  deleteBookCover: (id, expectedVersion) =>
    http.delete(`/admin/books/${id}/cover`, { params: { expectedVersion } }),
  currentPermissions: () => http.get("/me/permissions", { silent: true }),
  login: (body) => http.post("/login", body),
  sendLoginCode: (email) => http.post("/login/code", { email }),
  register: (body) => http.post("/login/register", body),
  resetPassword: (body) => http.post("/login/reset-password", body),
  logout: () => http.post("/logout"),
  // 通信查询沿用后端聚合接口，ID及筛选字段直接作为查询参数。
  contacts: (params) =>
    http.get("/members", { params: clean({ ...params, scene: "CONTACT" }) }),
  myMemberChanges: (params) =>
    http.get("/member-change-applications", { params: clean(params) }),
  conversations: (params) =>
    http.get("/conversations", { params: clean(params) }),
  createConversation: (body) => http.post("/conversations", body),
  updateConversation: (id, body) => http.put(`/conversations/${id}`, body),
  messages: (params, options = {}) =>
    http.get("/messages", { ...options, params: clean(params) }),
  sendMessage: (body) => http.post("/messages", body),
  readMessage: (id) => http.post(`/messages/${id}/read`),
  socketTicket: () => http.post("/ws/tickets", null, { silent: true }),
  members: (params) => http.get("/admin/members", { params: clean(params) }),
  updateMember: (id, body) => http.put(`/admin/members/${id}`, body),
  createMember: (body) => http.post("/admin/members", body),
  disableMember: (id, memberType) =>
    http.delete(`/admin/members/${id}`, { params: { memberType } }),
  myMember: (params) => http.get("/members", { params: clean(params) }),
  updateMyMember: (id, body) => http.put(`/members/${id}`, body),
  departments: (params) =>
    http.get("/admin/departments", { params: clean(params) }),
  createDepartment: (body) => http.post("/admin/departments", body),
  updateDepartment: (id, body) => http.put(`/admin/departments/${id}`, body),
  deleteDepartment: (id) => http.delete(`/admin/departments/${id}`),
  permissions: (params) =>
    http.get("/admin/permissions", {
      params: clean({ page: 1, pageSize: 10, ...params }),
    }),
  permission: (id) => http.get(`/admin/permissions/${id}`),
  createPermission: (body) => http.post("/admin/permissions", body),
  updatePermission: (id, body) => http.put(`/admin/permissions/${id}`, body),
  deletePermission: (id) => http.delete(`/admin/permissions/${id}`),
  memberChanges: (admin, params) =>
    http.get(`${admin ? "/admin" : ""}/member-change-applications`, {
      params: clean(params),
    }),
  createMemberChange: (body) => http.post("/member-change-applications", body),
  cancelMemberChange: (id, expectedVersion) =>
    http.post(`/member-change-applications/${id}/cancel`, { expectedVersion }),
  decideMemberChange: (id, body) =>
    http.post(`/admin/member-change-applications/${id}/decision`, body),
  books: (params) => http.get("/books", { params: clean(params) }),
  categories: (params) =>
    http.get("/book-categories", { params: clean(params) }),
  copies: (params) => http.get("/admin/book-copies", { params: clean(params) }),
  createBook: (body) => http.post("/admin/books", body),
  updateBook: (id, body) => http.put(`/admin/books/${id}`, body),
  deleteBook: (id) => http.delete(`/admin/books/${id}`),
  createCategory: (body) => http.post("/admin/book-categories", body),
  updateCategory: (id, body) => http.put(`/admin/book-categories/${id}`, body),
  deleteCategory: (id) => http.delete(`/admin/book-categories/${id}`),
  createCopy: (bookId, body) =>
    http.post(`/admin/books/${bookId}/copies`, body),
  updateCopy: (id, body) => http.put(`/admin/book-copies/${id}`, body),
  deleteCopy: (id) => http.delete(`/admin/book-copies/${id}`),
  applications: (admin, params) =>
    http.get(`${admin ? "/admin" : ""}/borrow-applications`, {
      params: clean(params),
    }),
  createApplication: (body) => http.post("/borrow-applications", body),
  cancelApplication: (id, expectedVersion) =>
    http.post(`/borrow-applications/${id}/cancel`, { expectedVersion }),
  approveApplication: (id, body) =>
    http.post(`/admin/borrow-applications/${id}/approve`, body),
  rejectApplication: (id, body) =>
    http.post(`/admin/borrow-applications/${id}/reject`, body),
  records: (admin, params) =>
    http.get(`${admin ? "/admin" : ""}/borrow-records`, {
      params: clean(params),
    }),
  returnBook: (id, body) =>
    http.post(`/admin/borrow-records/${id}/return`, body),
  returnBarcode: (barcode, body) =>
    http.post(`/admin/book-copies/${encodeURIComponent(barcode)}/return`, body),
  myPlan: (options = {}) => http.get("/reading-plans", options),
  createMyPlan: (body) => http.post("/reading-plans", body),
  updateMyPlan: (body) => http.put("/reading-plans", body),
  deleteMyPlan: (expectedVersion) =>
    http.delete("/reading-plans", { params: { expectedVersion } }),
  myPlanItems: (params) =>
    http.get("/reading-plans/items", { params: clean(params) }),
  addMyPlanItem: (body) => http.post("/reading-plans/items", body),
  updateMyPlanItem: (bookId, body) =>
    http.put(`/reading-plans/items/${bookId}`, body),
  deleteMyPlanItem: (bookId, expectedVersion, expectedPlanVersion) =>
    http.delete(`/reading-plans/items/${bookId}`, {
      params: { expectedVersion, expectedPlanVersion },
    }),
  adminPlans: (params) =>
    http.get("/admin/reading-plans", { params: clean(params) }),
  adminPlanItems: (params) =>
    http.get("/admin/reading-plans/items", { params: clean(params) }),
  createAdminPlan: (body) => http.post("/admin/reading-plans", body),
  updateAdminPlan: (memberId, body) =>
    http.put(`/admin/reading-plans/${memberId}`, body),
  deleteAdminPlan: (memberId, expectedVersion) =>
    http.delete(`/admin/reading-plans/${memberId}`, {
      params: { expectedVersion },
    }),
  addAdminPlanItem: (memberId, body) =>
    http.post(`/admin/reading-plans/${memberId}/items`, body),
  updateAdminPlanItem: (memberId, bookId, body) =>
    http.put(`/admin/reading-plans/${memberId}/items/${bookId}`, body),
  deleteAdminPlanItem: (
    memberId,
    bookId,
    expectedVersion,
    expectedPlanVersion,
  ) =>
    http.delete(`/admin/reading-plans/${memberId}/items/${bookId}`, {
      params: { expectedVersion, expectedPlanVersion },
    }),
};
