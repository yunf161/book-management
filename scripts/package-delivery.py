"""交付打包：白名单收集，检查本地真实凭据，生成逐文件SHA256。"""

import hashlib
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AI = ROOT / "ai-service"
RELEASE = ROOT / "target/releases/AI-library-1.0.0"
if RELEASE.exists() and "--refresh" not in sys.argv and "--check-layout" not in sys.argv:
    raise RuntimeError("交付目录已存在；请使用新版本或核对后另行处理，禁止静默覆盖")
# 目录整理后的只读预检；不创建交付目录，也不读取凭据内容。
if "--check-layout" in sys.argv:
    required = ["src", "frontend", "database", "deploy", "scripts", "tools", "tests", "docx", ".mvn",
                "pom.xml", "mvnw", "mvnw.cmd", "start.ps1", "docx/README.md"]
    missing = [name for name in required if not (ROOT / name).exists()]
    if missing:
        raise RuntimeError("交付输入缺失：" + ", ".join(missing))
    print(json.dumps({"layout": "PASS", "checked": len(required)}))
    sys.exit(0)
RELEASE.mkdir(parents=True, exist_ok=True)
excluded = {".git", ".idea", ".vscode", "node_modules", "target", "dist", "__pycache__", ".pytest_cache"}
source_files = []


def copy_file(source, relative):
    destination = RELEASE / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    source_files.append(destination)


def copy_tree(source, relative):
    for file in sorted(source.rglob("*")):
        parts = file.relative_to(source).parts
        if file.is_file() and not excluded.intersection(parts) and file.name != "local-secrets.properties":
            copy_file(file, Path(relative) / file.relative_to(source))


copy_file(ROOT / "target/AI-library-1.0.0.jar", "backend/AI-library-1.0.0.jar")
copy_tree(ROOT / "frontend/dist", "frontend")
for directory in ("src", "frontend", "database", "deploy", "scripts", "tools", "tests", "docx", ".mvn"):
    copy_tree(ROOT / directory, "source/java/" + directory)
for filename in ("pom.xml", "mvnw", "mvnw.cmd", "start.ps1", ".gitignore"):
    copy_file(ROOT / filename, "source/java/" + filename)
for directory in ("agents", "clients", "config", "prompts", "rag", "tools", "tests", "knowledge"):
    copy_tree(AI / directory, "source/java/ai-service/" + directory)
for file in AI.iterdir():
    if file.is_file() and (file.suffix == ".md" or file.name in {
        "ai_server.py", "assistant_cli.py", "consult_cli.py",
        "rag_cli.py", "recommendation_rerank.py", "rerank_server.py", "preflight.py", "requirements.txt",
        "start-ollama-igpu.ps1", "start-ai.ps1", ".gitignore",
    }):
        copy_file(file, "source/java/ai-service/" + file.name)
for directory in ("00-release", "11-delivery"):
    copy_tree(ROOT / "database" / directory, "database/" + directory)
copy_tree(ROOT / "deploy", "deploy")
# 完整收集说明目录，确保交付包中的相对链接仍然有效。
copy_tree(ROOT / "docx", "docx")

# 仅检测，不输出密钥值。扫描源码、静态产物以及JAR内本项目配置/代码，排除依赖二进制。
secrets = []
for line in (ROOT / "local-secrets.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line:
        name, value = line.split("=", 1)
        if re.search("PASSWORD|SECRET|TOKEN", name) and len(value.strip()) >= 8:
            secrets.append(value.strip().encode())
violations = []
for file in source_files:
    if file.suffix == ".jar":
        with zipfile.ZipFile(file) as archive:
            contents = [(name, archive.read(name)) for name in archive.namelist() if name.startswith("BOOT-INF/classes/") and not name.endswith("/")]
    else:
        contents = [(file.name, file.read_bytes())]
    for name, content in contents:
        if any(secret in content for secret in secrets):
            violations.append(str(file.relative_to(RELEASE)) + ":" + name)
if violations:
    raise RuntimeError("检测到本地凭据，停止交付：" + ", ".join(violations))

manifest = {"version": "1.0.0", "files": []}
for file in sorted(source_files):
    manifest["files"].append({"path": file.relative_to(RELEASE).as_posix(), "bytes": file.stat().st_size, "sha256": hashlib.sha256(file.read_bytes()).hexdigest()})
(RELEASE / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
(RELEASE / "SHA256SUMS.txt").write_text("\n".join(row["sha256"] + "  " + row["path"] for row in manifest["files"]) + "\n", encoding="utf-8")
archive = RELEASE.parent / (RELEASE.name + ".zip")
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as output:
    for file in sorted(RELEASE.rglob("*")):
        if file.is_file():
            output.write(file, "AI-library-1.0.0/" + file.relative_to(RELEASE).as_posix())
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix(".zip.sha256").write_text(digest + "  " + archive.name + "\n", encoding="utf-8")
print(json.dumps({"files": len(source_files), "bytes": archive.stat().st_size, "sha256": digest, "secretFindings": 0}))
