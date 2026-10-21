"""解析显式知识清单及UTF-8 Markdown/TXT，保留来源、版本和字符偏移。"""

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from rag.importing import FORMATS, read_structured_source, validate_metadata


def timestamp(value, default):
    if value is None:
        return default
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("必须带时区")
        return parsed.timestamp()
    except (ValueError, AttributeError, TypeError) as exc:
        raise ValueError("生效时间需使用带时区的ISO时间") from exc


def split_text(text, size, overlap):
    """优先在段落或句末切分，保证进度与最大长度；偏移相对规范化文本。"""
    if size <= 0 or not 0 <= overlap < size:
        raise ValueError("非法分段参数")
    start = 0
    while start < len(text):
        end = min(start + size, len(text))
        if end < len(text):
            boundaries = [text.rfind(mark, start + size // 2, end) for mark in ("\n\n", "。", "\n")]
            boundary = max(boundaries)
            if boundary >= 0:
                end = boundary + 1
        if text[start:end].strip():
            yield start, end, text[start:end]
        if end == len(text):
            break
        start = max(start + 1, end - overlap)


def load_manifest(path, settings):
    """一份清单代表完整知识快照；先校验全部资料，再允许入库。"""
    path = Path(path).resolve()
    manifest = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(manifest, dict) or not isinstance(manifest.get("documents"), list):
        raise ValueError("知识清单必须含documents数组")
    current = manifest.get("current_policies", {})
    if not isinstance(current, dict):
        raise ValueError("current_policies必须是政策ID到版本的对象")
    rows, seen, policy_versions = [], set(), set()
    content_groups, version_content = {}, {}
    for doc in manifest["documents"]:
        if not isinstance(doc, dict):
            raise ValueError("每个文档必须为对象")
        for key in ("id", "file", "title", "source", "kind", "version", "status"):
            if not isinstance(doc.get(key), str) or not doc[key].strip():
                raise ValueError("文档缺少有效字符串字段：" + key)
        if doc["id"] in seen:
            raise ValueError("文档ID重复")
        seen.add(doc["id"])
        if doc["kind"] not in ("policy", "general", "book_intro") or doc["status"] not in ("ACTIVE", "DRAFT", "ARCHIVED"):
            raise ValueError("非法文档类型或状态")
        if not isinstance(doc.get("demo"), bool):
            raise ValueError("必须明确设置demo为true或false")
        file = (path.parent / doc["file"]).resolve()
        extension = file.suffix.lower().removeprefix(".")
        if not file.is_relative_to(path.parent) or extension not in FORMATS:
            raise ValueError("仅支持知识目录内的MD/TXT/CSV/JSON/PDF文件，不允许路径越界")
        parsed_metadata = None
        if extension in ("csv", "json", "pdf"):
            parsed = read_structured_source(file)
            text, parsed_metadata = parsed["content"], parsed["importMetadata"]
        else:
            # 保留现有本地Markdown/TXT快照的200万字符上限。
            text = file.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()
        if not text or len(text) > 2_000_000:
            raise ValueError("知识文档为空或超过200万字符")
        content_md5 = hashlib.md5(text.encode("utf-8"), usedforsecurity=False).hexdigest()
        imported = validate_metadata(parsed_metadata or doc.get("importMetadata"), text)
        policy_id = doc.get("policy_id", "")
        eligible = doc["status"] == "ACTIVE"
        if doc["kind"] == "policy":
            if not isinstance(policy_id, str) or not policy_id.strip():
                raise ValueError("政策必须提供policy_id")
            pair = (policy_id, doc["version"])
            if pair in version_content and version_content[pair] != text:
                raise ValueError("同一政策同一版本存在冲突正文")
            version_content[pair] = text
            policy_versions.add(pair)
            eligible = eligible and current.get(policy_id) == doc["version"]
        elif policy_id:
            raise ValueError("非政策文档不能提供policy_id")
        begin = timestamp(doc.get("effective_from"), 0)
        end = timestamp(doc.get("effective_to"), 253402300799)
        if begin >= end:
            raise ValueError("生效区间不合法")
        # MD5用于内容去重，不用于鉴权或可信性证明。不同政策版本/生效边界不可合并。
        # 保留正文再比较，避免MD5碰撞导致不同资料误合并。
        dedup_key = (content_md5, doc["kind"], policy_id, doc["version"],
                     doc["status"], doc["demo"], begin, end)
        provenance = {"document_id": doc["id"], "title": doc["title"], "source": doc["source"]}
        group = content_groups.get(dedup_key)
        if group is not None:
            if group["text"] != text:
                raise ValueError("MD5碰撞：不同正文具有相同指纹，拒绝导入")
            group["sources"].append(provenance)
            for row in group["rows"]:
                row["metadata"]["provenance_json"] = json.dumps(group["sources"], ensure_ascii=False)
            continue
        group = {"text": text, "sources": [provenance], "rows": []}
        content_groups[dedup_key] = group
        for index, (start, stop, content) in enumerate(split_text(text, settings.chunk_size, settings.overlap)):
            metadata = {
                "document_id": doc["id"], "title": doc["title"], "source": doc["source"],
                "kind": doc["kind"], "version": doc["version"], "policy_id": policy_id,
                "status": doc["status"], "eligible": eligible, "demo": doc["demo"],
                "effective_from": begin, "effective_to": end,
                "chunk_index": index, "start": start, "end": stop,
                "content_md5": content_md5,
                "provenance_json": json.dumps(group["sources"], ensure_ascii=False),
            }
            if imported:
                metadata["import_format"] = imported["format"]
                metadata["source_file"] = imported["fileName"]
                metadata["source_locations"] = json.dumps([
                    section["locator"] for section in imported["sections"]
                    if section["start"] < stop and section["end"] > start
                ], ensure_ascii=False)
            row = {"text": content, "metadata": metadata}
            rows.append(row)
            group["rows"].append(row)
    for key, version in current.items():
        if not isinstance(version, str) or (key, version) not in policy_versions:
            raise ValueError("当前政策指针指向不存在的版本")
        if not any(r["metadata"]["policy_id"] == key and r["metadata"]["version"] == version and r["metadata"]["eligible"] for r in rows):
            raise ValueError("当前政策指针必须指向ACTIVE文档")
    canonical = json.dumps(rows, ensure_ascii=False, sort_keys=True)
    return rows, hashlib.sha256(canonical.encode()).hexdigest()
