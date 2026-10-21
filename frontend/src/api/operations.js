import http from "./http";
const clean = (value) =>
  Object.fromEntries(
    Object.entries(value || {}).filter(
      ([, item]) => item !== "" && item !== null && item !== undefined,
    ),
  );
export const operationsApi = {
  overview: (params) => http.get("/admin/operations/overview", { params }),
  jobs: (params) =>
    http.get("/admin/operations/jobs", { params: clean(params) }),
  upload: (file) => {
    const body = new FormData();
    body.append("file", file);
    return http.post("/admin/operations/imports", body);
  },
  export: (body) => http.post("/admin/operations/exports", body),
  template: () =>
    http.get("/admin/operations/template", { responseType: "blob" }),
  file: (id) =>
    http.get(`/admin/operations/jobs/${encodeURIComponent(id)}/file`, {
      responseType: "blob",
    }),
};
