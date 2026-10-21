"""校验生成接口结构、引用和导出源码哈希，不访问业务服务。"""
import hashlib
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
spec = json.loads((root / "docx/project/openapi.json").read_text(encoding="utf-8"))
inventory = json.loads((root / "target/project-delivery/api-inventory.json").read_text(encoding="utf-8"))
expected = {(item["path"], item["method"].lower()) for item in inventory["endpoints"]}
actual = {(path, method) for path, methods in spec["paths"].items() for method in methods}
assert expected == actual, "OpenAPI方法和路径必须与Spring实际注册信息一致"
operations = [value for methods in spec["paths"].values() for value in methods.values()]
assert len({value["operationId"] for value in operations}) == len(operations)
for path, methods in spec["paths"].items():
    names = set(re.findall(r"\{([^}]+)\}", path))
    for value in methods.values():
        assert names == {p["name"] for p in value["parameters"] if p["in"] == "path"}, path
        assert all(p["required"] for p in value["parameters"] if p["in"] == "path"), path

def refs(value):
    if isinstance(value, dict):
        if "$ref" in value:
            reference = value["$ref"]
            assert reference.startswith("#/components/schemas/"), reference
            assert reference.rsplit("/", 1)[1] in spec["components"]["schemas"], reference
        for item in value.values(): refs(item)
    elif isinstance(value, list):
        for item in value: refs(item)
refs(spec)
assert "text/event-stream" in spec["paths"]["/ai/chat"]["post"]["responses"]["200"]["content"]
assert "302" in spec["paths"]["/books/{bookId}/cover/content"]["get"]["responses"]
latest = json.loads((root / "target/project-delivery/latest.json").read_text(encoding="utf-8"))
folder = root / latest["directory"]
manifest = json.loads((folder / "SOURCE-MANIFEST.json").read_text(encoding="utf-8"))
for entry in manifest["files"]:
    file = folder / entry["path"]
    assert hashlib.sha256(file.read_bytes()).hexdigest() == entry["sha256"], entry["path"]
    assert file.name != "local-secrets.properties"
    assert not any(part in {".git", "node_modules", "target", "data", "dist"} for part in Path(entry["path"]).parts)
assert hashlib.sha256((root / latest["archive"]).read_bytes()).hexdigest() == latest["archiveSha256"]
result = {"springOperations": len(expected), "openApiOperations": len(actual), "uniqueOperationIds": True,
          "resolvedSchemaReferences": True, "pathParametersValid": True, "verifiedSourceHashes": len(manifest["files"]),
          "archiveHashValid": True, "privateRuntimePathsExcluded": True}
(root / "target/project-delivery/document-checks.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
