"""从新装DDL提取表和字段索引；约束原文仍以SQL为准。"""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sql = (root / "database/00-release/000-schema.sql").read_text(encoding="utf-8")
tables = re.findall(r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?([a-z_]+)`?\s*\(([\s\S]*?);", sql, re.I)
lines = ["# 数据表与字段索引", "", "从当前新装DDL生成；逻辑关联、状态语义见系统设计，索引和CHECK约束请直接阅读SQL。不是生产数据库数据导出。", "",
         f"当前新装脚本包含 **{len(tables)} 张表**。", "", "| 表 |", "| --- |"]
lines += [f"| [{name}](#{name}) |" for name, _ in tables]
for name, body in tables:
    lines += ["", f"## {name}", "", "| 字段 | SQL类型/声明摘要 |", "| --- | --- |"]
    for row in body.splitlines():
        match = re.match(r"\s*`?([a-z_][a-z_0-9]*)`?\s+((?:bigint|int|integer|varchar|char|text|longtext|datetime|timestamp|date|decimal|boolean|tinyint|json|double|mediumtext|blob)\b.*)", row, re.I)
        if match:
            field, declaration = match.groups()
            declaration = declaration.rstrip(",").replace("|", "\\|").replace("`", "")
            lines.append(f"| {field} | {declaration} |")
lines += ["", "完整结构：[000-schema.sql](../../database/00-release/000-schema.sql)。当前无AI成员档案表；AI任务、能力控制、人工交接、模型用量等属于服务模块，继续保留。", ""]
(root / "docx/project/database-reference.md").write_text("\n".join(lines), encoding="utf-8")
print(f"Generated {len(tables)} table definitions")
