import http from "./http";

export const knowledgeApi = {
  documents: (params) => http.get("/admin/knowledge/documents", { params }),
  document: (id) =>
    http.get(`/admin/knowledge/documents/${encodeURIComponent(id)}`),
  status: () => http.get("/admin/knowledge/status"),
  preview: (file) => {
    const body = new FormData();
    body.append("file", file);
    return http.post("/admin/knowledge/import-preview", body, {
      timeout: 30000,
    });
  },
  save: (id, body) =>
    id
      ? http.put(`/admin/knowledge/documents/${encodeURIComponent(id)}`, body)
      : http.post("/admin/knowledge/documents", body),
  rebuild: (body) => http.post("/admin/knowledge/rebuild", body),
  search: (body) =>
    http.post("/admin/knowledge/search", body, { timeout: 35000 }),
};
