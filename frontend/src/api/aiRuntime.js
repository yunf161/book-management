import http from "./http";
export const runtimeApi = {
  usage: (params) => http.get("/ai/usage", { params }),
  tasks: (params) => http.get("/ai/tasks", { params }),
  submit: (body) => http.post("/ai/tasks", body),
  retry: (id) => http.post(`/ai/tasks/${encodeURIComponent(id)}/retry`),
  cancel: (id) => http.post(`/ai/tasks/${encodeURIComponent(id)}/cancel`),
  controls: () => http.get("/admin/ai-controls"),
  update: (code, body) => http.put(`/admin/ai-controls/${code}`, body),
  reconcile: () => http.post("/admin/ai-actions/reconcile"),
};
