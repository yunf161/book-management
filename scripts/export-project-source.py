"""白名单导出当前源码，不携带开发历史/私有数据；不修改Git，不连接远程。"""
import hashlib
import json
import os
import re
import shutil
import zipfile
from datetime import datetime
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "target/project-delivery"
stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
destination = OUTPUT / f"AI-library-source-{stamp}"
selected = {}

def include(file, name=None):
    relative = file.relative_to(ROOT)
    if any(part in {"target", "node_modules", "dist", ".git", "__pycache__", "data", ".venv", ".pytest_cache"} for part in relative.parts): return
    if file.suffix.lower() in {".pyc", ".log", ".sqlite", ".db", ".pem", ".key", ".p12", ".pfx"}: return
    if file.name.startswith(".env") and file.name != ".env.example": return
    if file.name == "local-secrets.properties": return
    selected[name or relative.as_posix()] = file

for name in ["README.md", "pom.xml", "mvnw", "mvnw.cmd", "start.ps1", ".gitignore", ".gitattributes"]:
    include(ROOT / name)
for folder in ["src/main", "src/test", "frontend/src", "docx/project", "deploy", "scripts"]:
    for file in (ROOT / folder).rglob("*"):
        if file.is_file(): include(file)
for file in (ROOT / ".mvn").rglob("*.properties"): include(file)
for name in ["package.json", "package-lock.json", "vite.config.js", "index.html", ".gitignore"]:
    include(ROOT / "frontend" / name)
for file in (ROOT / "frontend/tests").glob("*.test.mjs"): include(file)
for file in (ROOT / "frontend/tests/visual").rglob("*"):
    if file.is_file(): include(file)
for folder in ["agents", "clients", "config", "rag", "tools", "prompts", "knowledge", "tests"]:
    for file in (ROOT / "ai-service" / folder).rglob("*"):
        if file.is_file(): include(file)
for pattern in ["*.py", "*.ps1", "requirements.txt", ".gitignore"]:
    for file in (ROOT / "ai-service").glob(pattern): include(file)
for file in (ROOT / "database").rglob("*.sql"): include(file)
include(ROOT / "database/README.md")
# 发布版演示稿不复制含本机账号/业务ID的历史长稿。
include(ROOT / "docx/project/demo.md", "演示脚本.md")

missing = [name for name, file in selected.items() if not file.is_file()]
if missing: raise SystemExit("缺少白名单文件：" + ",".join(missing))
findings = []
# 与本机已配置密钥做精确比对；不输出值，也不把本机配置复制到发布树。
known_secrets = set()
secret_name = re.compile(r"(?:PASSWORD|API_KEY|ACCESS_KEY_SECRET|INTERNAL_TOKEN|AI_TOKEN|JWT_SECRET)$")
local_file = ROOT / "local-secrets.properties"
if local_file.is_file():
    for row in local_file.read_text(encoding="utf-8-sig").splitlines():
        if "=" in row and not row.lstrip().startswith("#"):
            name, value = row.split("=", 1)
            if secret_name.search(name.strip()) and len(value.strip()) >= 8:
                known_secrets.add(value.strip())
for name, value in os.environ.items():
    if secret_name.search(name) and len(value) >= 8:
        known_secrets.add(value)
if os.name == "nt":
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as registry:
        for name in ["DASHSCOPE_API_KEY", "OSS_ACCESS_KEY_SECRET", "ALIBABA_CLOUD_ACCESS_KEY_SECRET"]:
            try:
                value = winreg.QueryValueEx(registry, name)[0]
                if isinstance(value, str) and len(value) >= 8: known_secrets.add(value)
            except FileNotFoundError:
                pass
known_secrets = {value for value in known_secrets if "REPLACE" not in value and not value.startswith("${")}
patterns = {
    "api-key": re.compile(r"\bsk-[A-Za-z0-9_.-]{24,}"),
    "cloud-access-key": re.compile(r"\b(?:LTAI[A-Za-z0-9]{16,}|AKIA[A-Z0-9]{16})\b"),
    "private-key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
}
for name, file in selected.items():
    try: content = file.read_text(encoding="utf-8-sig")
    except UnicodeError: continue
    for label, pattern in patterns.items():
        for match in pattern.finditer(content):
            findings.append({"file": name, "line": content.count("\n", 0, match.start()) + 1, "kind": label})
    for value in known_secrets:
        if value in content:
            findings.append({"file": name, "line": content.count("\n", 0, content.index(value)) + 1, "kind": "configured-secret"})
OUTPUT.mkdir(parents=True, exist_ok=True)
if findings:
    (OUTPUT / "blocked-secrets.json").write_text(json.dumps(findings, indent=2), encoding="utf-8")
    raise SystemExit(f"导出已阻止：发现 {len(findings)} 个高置信模式；报告只记录位置，不输出密钥。")

destination.mkdir()
manifest = []
for name, file in sorted(selected.items()):
    target = destination / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(file, target)
    manifest.append({"path": name, "bytes": target.stat().st_size, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})

# 所有发布文档的相对文件链接必须在干净源码树中存在；锚点由Markdown阅读器处理。
broken = []
for file in [destination / "README.md", *(destination / "docx/project").glob("*.md"), destination / "database/README.md"]:
    for match in re.finditer(r"(?<!!)\[[^\]]*\]\(([^)]+)\)", file.read_text(encoding="utf-8")):
        link = match.group(1).strip("<>")
        if re.match(r"^[a-zA-Z]+:", link) or link.startswith("#"): continue
        link = unquote(link.split("#", 1)[0])
        # Windows长目录先规范化，避免包含../的有效路径被MAX_PATH误判为不存在。
        if link and not (file.parent / link).resolve().is_file(): broken.append({"file": file.relative_to(destination).as_posix(), "link": link})
if broken:
    (OUTPUT / "broken-links.json").write_text(json.dumps(broken, ensure_ascii=False, indent=2), encoding="utf-8")
    raise SystemExit("发布文档存在缺失链接，未生成ZIP。")

metadata = {"source": "current working tree; no Git history", "files": manifest,
            "checks": {"highConfidenceSecretMatches": 0, "brokenDocumentLinks": 0,
            "note": "规则扫描和白名单不是绝对安全证明；测试用固定密钥不用于生产。"}}
(destination / "SOURCE-MANIFEST.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
archive = destination.with_suffix(".zip")
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
    for file in sorted(destination.rglob("*")):
        if file.is_file(): zipped.write(file, "AI-library/" + file.relative_to(destination).as_posix())
result = {"directory": destination.relative_to(ROOT).as_posix(), "archive": archive.relative_to(ROOT).as_posix(),
          "files": len(manifest), "archiveBytes": archive.stat().st_size,
          "archiveSha256": hashlib.sha256(archive.read_bytes()).hexdigest(), "remotePushed": False}
(OUTPUT / "latest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
