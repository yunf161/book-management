"""从Spring测试导出的实际路由生成Markdown与OpenAPI，不猜测Service必填条件。"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
inventory = json.loads((ROOT / "target/project-delivery/api-inventory.json").read_text(encoding="utf-8"))
OUT = ROOT / "docx/project"
OUT.mkdir(parents=True, exist_ok=True)
models = inventory["models"]

def key(name):
    return name.replace("com.yunfeil161.ailibrary.", "").replace(".", "_").replace("$", "_")

def schema(info):
    kind = info.get("rawType", info.get("type", "java.lang.String"))
    if "enum" in info:
        return {"type": "string", "enum": info["enum"]}
    if kind in models:
        return {"$ref": "#/components/schemas/" + key(kind)}
    if kind in ("java.lang.Long", "long", "java.lang.Integer", "int", "java.lang.Short"):
        return {"type": "integer", "format": "int64" if "Long" in kind or kind == "long" else "int32"}
    if kind in ("java.lang.Boolean", "boolean"):
        return {"type": "boolean"}
    if kind in ("java.math.BigDecimal", "double", "java.lang.Double", "float"):
        return {"type": "number"}
    if "MultipartFile" in kind:
        return {"type": "string", "format": "binary"}
    if kind == "java.time.LocalDate":
        return {"type": "string", "format": "date"}
    if kind == "java.time.LocalDateTime":
        return {"type": "string", "description": "业务本地时间，ISO 8601，无偏移量"}
    if "List" in kind or "Set" in kind:
        generic = re.search(r"<(.*)>", info.get("type", ""))
        return {"type": "array", "items": schema({"rawType": generic.group(1)}) if generic else {}}
    if "Map" in kind or kind == "java.lang.Object":
        return {"type": "object", "additionalProperties": True}
    return {"type": "string"}

def annotation(param, name):
    return param["annotations"].get(name, {})

def param_name(param):
    for annotation_name in ("RequestParam", "PathVariable", "RequestHeader", "RequestPart"):
        values = annotation(param, annotation_name)
        if values.get("name") or values.get("value"):
            return values.get("name") or values["value"]
    return param["name"] or "value"

def required(param):
    if param["binding"] == "path": return True
    values = next((param["annotations"][name] for name in ("RequestParam", "RequestHeader", "RequestPart", "RequestBody") if name in param["annotations"]), {})
    default = values.get("defaultValue", "")
    return values.get("required", True) and not (default and not default.startswith("\n"))

schemas = {key(name): {"type": "object", "properties": {field: schema(info) for field, info in fields.items()},
            "description": f"{name}；属性是否必填及跨字段约束由Service校验，见接口说明。"} for name, fields in models.items()}
schemas["Result"] = {"type": "object", "properties": {"code": {"type": "integer", "description": "成功200，失败与HTTP状态一致"}, "msg": {"type": "string"}, "data": {"description": "业务对象、PageResult或null；当前控制器使用非泛型Result，未伪造响应实体绑定。"}}}
spec = {"openapi": "3.0.3", "info": {"title": "阅知图书馆 API", "version": "1.0.0",
        "description": "由当前Spring路由与请求类型生成。包含兼容别名；JSON对象内业务必填规则以Service为准。响应data使用开放类型，详见配套Markdown。"},
        "servers": [{"url": "http://localhost:8080", "description": "本机Java直连"}, {"url": "http://localhost:5173/api", "description": "开发前端代理"}],
        "paths": {}, "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}}, "schemas": schemas}}
lines = ["# HTTP 接口参考", "", "基线：2026-10-21。此清单来自实际Spring注册路由，不来自早期原型。", "",
         f"共 **{len(inventory['endpoints'])} 个 HTTP 方法＋路径注册项**、**{len(models)} 个请求模型**；`/members` 等别名单独计数，不等于同等数量的独立业务功能。", "",
         "可导入 Apifox/Postman 的结构文件：[openapi.json](openapi.json)。常见协议和业务约束见 [API约定](api-conventions.md)。", "",
         "请求模型内属性不统一标为必填：不少规则在Service中按申请类型、成员身份、记录状态校验。开放Map模型和非泛型Result也未伪造完整契约。", ""]
current = None
for index, endpoint in enumerate(inventory["endpoints"], 1):
    controller = endpoint["controller"].split(".")[-1]
    if controller != current:
        lines += [f"## {controller}", ""]
        current = controller
    path, method = endpoint["path"], endpoint["method"]
    public = (method == "GET" and (path in ("/books", "/book-categories", "/policies/current") or path.startswith("/books/"))) or path.startswith("/login")
    operation = {"operationId": f"{controller}_{endpoint['handler']}_{index}", "tags": [controller],
                 "summary": f"{controller}.{endpoint['handler']}", "security": [] if public else [{"bearerAuth": []}], "parameters": [],
                 "responses": {str(code): {"description": "成功（data结构见实现）" if code == 200 else "按统一异常处理返回；是否发生取决于业务场景",
                   "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Result"}}}} for code in (200, 400, 401, 403, 404, 409, 422, 429, 500, 503)}}
    params_text = []
    form_fields, form_required = {}, []
    multipart = "multipart/form-data" in endpoint["consumes"]
    for param in endpoint["parameters"]:
        name, binding = param_name(param), param["binding"]
        if binding == "header" and name.lower() == "authorization": continue
        model = param["rawType"] in models
        label = f"[{key(param['rawType'])}](#{key(param['rawType']).lower()})" if model else f"`{param['type']}`"
        params_text.append(f"| {name} | {'form-data' if multipart and binding in ('query', 'part') else binding} | {label} |")
        if binding == "body":
            operation["requestBody"] = {"required": required(param), "content": {"application/json": {"schema": schema(param)}}}
        elif binding == "part" or (multipart and binding == "query"):
            form_fields[name] = schema(param)
            if required(param): form_required.append(name)
        elif binding == "model" and model:
            operation["parameters"] += [{"name": field, "in": "query", "required": False, "schema": schema(info)} for field, info in models[param["rawType"]].items()]
        elif "Map" not in param["rawType"]:
            operation["parameters"].append({"name": name, "in": "header" if binding == "header" else "path" if binding == "path" else "query", "required": required(param), "schema": schema(param)})
        else:
            operation["description"] = "包含开放Map参数；其允许字段见API约定和对应Service。"
    if form_fields:
        operation["requestBody"] = {"required": True, "content": {"multipart/form-data": {"schema": {"type": "object", "properties": form_fields, "required": form_required}}}}
    if path == "/ai/chat":
        operation["responses"]["200"] = {"description": "SSE：meta/delta/sources/proposal/usage/done/error；收到done才成功", "content": {"text/event-stream": {"schema": {"type": "string"}}}}
    if path.endswith("/cover/content"):
        operation["responses"].pop("200")
        operation["responses"]["302"] = {"description": "跳转到短期OSS签名地址；禁止缓存签名", "headers": {"Location": {"schema": {"type": "string", "format": "uri"}}}}
    if method == "GET" and (path.endswith("/template") or path.endswith("/file")):
        operation["responses"]["200"] = {"description": "授权下载Excel文件", "content": {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": {"schema": {"type": "string", "format": "binary"}}}}
    spec["paths"].setdefault(path, {})[method.lower()] = operation
    source = endpoint["controller"].replace(".", "/") + ".java"
    lines += [f"### `{method} {path}`", "", f"处理方法：[{controller}.{endpoint['handler']}](../../src/main/java/{source})。{'公开能力；规则与限流校验仍有效。' if public else '需要登录；具体权限编码和数据范围在Controller或Service中校验。'}", ""]
    if params_text: lines += ["| 参数 | 绑定位置 | 类型 |", "| --- | --- | --- |", *params_text, ""]
    else: lines += ["无显式业务参数。", ""]
    if path == "/ai/chat": lines += ["返回SSE，不能当作普通Result JSON解析。", ""]
lines += ["# 请求模型字段索引", "", "下表包含继承字段；枚举弃用值不再作为可选项。`expectedVersion` 等内部版本仍由前端自动携带，不由用户手工输入。", ""]
for name, fields in models.items():
    lines += [f"## {key(name)}", "", f"`{name}`", "", "| 字段 | Java类型 | 可选枚举 |", "| --- | --- | --- |"]
    for field, info in fields.items():
        lines.append(f"| {field} | `{info['type']}` | {'、'.join(info.get('enum', [])) or '—'} |")
    lines.append("")
(OUT / "api-reference.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
(OUT / "openapi.json").write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(inventory['endpoints'])} method/path entries and {len(models)} request models")
