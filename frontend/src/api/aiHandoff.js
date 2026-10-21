import http from "./http";
export const handoffApi = {
  query: (params) => http.get("/ai/handoffs", { params }),
  create: (body) => http.post("/ai/handoffs", body),
  decide: (id, body) => http.post(`/ai/handoffs/${id}/decision`, body),
};
