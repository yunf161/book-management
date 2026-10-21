"""受控文件转正文：仅解析数据，不读取客户端路径、不执行公式或文档脚本。"""

import base64
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import threading

MAX_FILE_BYTES = 4 * 1024 * 1024
MAX_TEXT_BYTES = 600000
MAX_CHARACTERS = 200000
MAX_SECTIONS = 5000
FORMATS = {"md", "txt", "csv", "json", "pdf"}
PARSING_SLOTS = threading.BoundedSemaphore(2)


class ImportValidationError(ValueError):
    status = 400


def normalize(text):
    return text.replace("\r\n", "\n").replace("\r", "\n").strip()


def content_md5(text):
    return hashlib.md5(normalize(text).encode("utf-8"), usedforsecurity=False).hexdigest()


def filename_format(name):
    if (not isinstance(name, str) or not name.strip() or len(name) > 200
            or any(char in name for char in '/\\\x00\r\n')):
        raise ImportValidationError("文件名无效，请使用不含路径的文件名")
    extension = Path(name).suffix.lower().removeprefix(".")
    if extension not in FORMATS:
        raise ImportValidationError("仅支持MD、TXT、CSV、JSON和文本型PDF")
    return extension


def validate_metadata(value, text):
    """来源位置必须与确认保存的正文匹配；修改正文后不得沿用过期偏移。"""
    if value is None:
        return None
    if not isinstance(value, dict) or set(value) != {"format", "fileName", "contentMd5", "sections"}:
        raise ImportValidationError("导入来源信息格式错误")
    if filename_format(value["fileName"]) != value["format"]:
        raise ImportValidationError("文件名和导入格式不一致")
    if value["contentMd5"] != content_md5(text):
        raise ImportValidationError("正文已修改，请重新解析文件或清除导入来源后保存")
    sections = value["sections"]
    if not isinstance(sections, list) or not 1 <= len(sections) <= MAX_SECTIONS:
        raise ImportValidationError("来源段数超出限制")
    previous = 0
    for section in sections:
        if (not isinstance(section, dict) or set(section) != {"start", "end", "locator"}
                or type(section["start"]) is not int or type(section["end"]) is not int
                or not previous <= section["start"] < section["end"] <= len(text)
                or not isinstance(section["locator"], str) or not 1 <= len(section["locator"]) <= 120):
            raise ImportValidationError("导入来源位置无效")
        previous = section["end"]
    return value


def parse_file(name, data):
    extension = filename_format(name)
    if not data or len(data) > (MAX_FILE_BYTES if extension == "pdf" else MAX_TEXT_BYTES):
        raise ImportValidationError("PDF最大4MiB，其他格式最大600000字节，文件不能为空")
    warnings = []
    if extension == "pdf":
        units, warnings, total_units = pdf_units(data)
    else:
        try:
            text = data.decode("utf-8-sig")
        except UnicodeError as error:
            raise ImportValidationError("文本必须使用UTF-8编码") from error
        if re.search(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", text):
            raise ImportValidationError("文本含非法控制字符或不是文本文件")
        if extension == "csv":
            units = csv_units(text)
        elif extension == "json":
            units = json_units(text)
        else:
            units = [("全文", text)]
        total_units = len(units)

    content, sections = "", []
    for locator, text in units:
        text = normalize(text)
        if not text:
            continue
        block = text if extension in ("md", "txt") else f"[{extension.upper()} {locator}]\n{text}"
        start = len(content) + (2 if content else 0)
        if start + len(block) > MAX_CHARACTERS:
            raise ImportValidationError("提取正文超过20万字符，请拆分文件后导入")
        content += ("\n\n" if content else "") + block
        sections.append({"start": start, "end": len(content), "locator": locator})
    if not sections:
        raise ImportValidationError("未提取到正文；扫描PDF请先OCR转换为文本型PDF或UTF-8文本")
    metadata = {"format": extension, "fileName": name, "contentMd5": content_md5(content), "sections": sections}
    return {"fileName": name, "format": extension, "content": content, "characters": len(content),
            "units": total_units, "warnings": warnings, "importMetadata": metadata}


def csv_units(text):
    csv.field_size_limit(MAX_CHARACTERS)
    reader = csv.reader(io.StringIO(text, newline=""), strict=True)
    try:
        headers = [name.strip() for name in next(reader)]
        if (not headers or len(headers) > 100 or any(not name for name in headers)
                or len(set(headers)) != len(headers)):
            raise ImportValidationError("CSV表头不能为空、不能重复，最多100列")
        result, previous_line = [], reader.line_num
        for row in reader:
            first_line = previous_line + 1
            previous_line = reader.line_num
            if not row or not any(cell.strip() for cell in row):
                continue
            if len(row) != len(headers):
                raise ImportValidationError(f"CSV第{first_line}行列数与表头不一致")
            if len(result) >= MAX_SECTIONS:
                raise ImportValidationError("CSV最多5000条记录")
            result.append((f"第{first_line}行", "\n".join(f"{name}: {value}" for name, value in zip(headers, row))))
        return result
    except (csv.Error, StopIteration) as error:
        raise ImportValidationError("CSV格式错误或缺少表头") from error


def json_units(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ImportValidationError("JSON包含重复字段")
            result[key] = value
        return result

    def invalid_constant(_):
        raise ImportValidationError("JSON不允许NaN或Infinity")

    def depth(value, level=0):
        if level > 12:
            raise ImportValidationError("JSON嵌套不能超过12层")
        if isinstance(value, (dict, list)):
            for child in (value.values() if isinstance(value, dict) else value):
                depth(child, level + 1)
        elif isinstance(value, float) and not math.isfinite(value):
            raise ImportValidationError("JSON数字超出可解析范围")
        elif isinstance(value, str):
            try:
                value.encode("utf-8")
            except UnicodeError as error:
                raise ImportValidationError("JSON包含非法Unicode字符") from error

    try:
        value = json.loads(text, object_pairs_hook=pairs, parse_constant=invalid_constant)
        depth(value)
    except ImportValidationError:
        raise
    except (ValueError, RecursionError) as error:
        raise ImportValidationError("JSON格式错误或嵌套过深，不支持JSONL") from error
    objects = [value] if isinstance(value, dict) else value
    if (not isinstance(objects, list) or not 1 <= len(objects) <= 2000
            or any(not isinstance(obj, dict) or not obj for obj in objects)):
        raise ImportValidationError("JSON需为非空对象或非空对象数组，最多2000个对象")
    return [("$" if isinstance(value, dict) else f"$[{index}]",
             json.dumps(obj, ensure_ascii=False, sort_keys=True, indent=2)) for index, obj in enumerate(objects)]


def pdf_units(data):
    from pypdf import PdfReader
    try:
        if not data.startswith(b"%PDF-"):
            raise ImportValidationError("文件不是有效PDF")
        reader = PdfReader(io.BytesIO(data), strict=True)
        if reader.is_encrypted:
            raise ImportValidationError("暂不支持加密PDF，请解密后导入")
        if not 1 <= len(reader.pages) <= 100:
            raise ImportValidationError("PDF最多100页")
        result, empty, characters = [], 0, 0
        for number, page in enumerate(reader.pages, 1):
            stream = page.get_contents()
            if stream is not None and len(stream.get_data()) > 2 * 1024 * 1024:
                raise ImportValidationError("PDF单页内容过大，请拆分或简化后导入")
            text = page.extract_text() or ""
            characters += len(text)
            if characters > MAX_CHARACTERS:
                raise ImportValidationError("PDF提取正文超过20万字符")
            if text.strip():
                result.append((f"第{number}页", text))
            else:
                empty += 1
        warnings = [f"有{empty}页未提取到文本，可能为空白页或扫描页，请核对预览"] if empty else []
        return result, warnings, len(reader.pages)
    except ImportValidationError:
        raise
    except Exception as error:
        raise ImportValidationError("PDF损坏、结构过于复杂或无法提取文本") from error


def preview(body):
    """所有格式在有时限/内存限制的子进程解析；最多并发两个，不触发向量写入。"""
    if not isinstance(body, dict) or set(body) != {"fileName", "contentBase64"}:
        raise ImportValidationError("文件预览参数错误")
    extension = filename_format(body["fileName"])
    encoded = body["contentBase64"]
    limit = MAX_FILE_BYTES if extension == "pdf" else MAX_TEXT_BYTES
    if not isinstance(encoded, str) or not 0 < len(encoded) <= ((limit + 2) // 3) * 4:
        raise ImportValidationError("文件超出导入大小限制")
    try:
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, TypeError) as error:
        raise ImportValidationError("文件编码错误") from error
    if not data or len(data) > limit:
        raise ImportValidationError("文件为空或超出大小限制")
    if not PARSING_SLOTS.acquire(blocking=False):
        error = ImportValidationError("文件解析繁忙，请稍后重试")
        error.status = 503
        raise error
    try:
        result = subprocess.run(
            [sys.executable, "-m", "rag.import_worker"], cwd=Path(__file__).resolve().parents[1],
            input=json.dumps(body).encode(), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15,
        )
        if result.returncode != 0 or len(result.stdout) > 1800000:
            raise ImportValidationError("文件解析失败或资源超限，请拆分文件重试")
        value = json.loads(result.stdout)
        if "error" in value:
            raise ImportValidationError(value["error"])
        return value
    except subprocess.TimeoutExpired as error:
        raise ImportValidationError("文件解析超过15秒，请拆分或简化文件") from error
    finally:
        PARSING_SLOTS.release()


def read_structured_source(file):
    """供已校验目录边界的本地清单复用隔离解析器，不接收HTTP客户端路径。"""
    extension = filename_format(file.name)
    maximum = MAX_FILE_BYTES if extension == "pdf" else MAX_TEXT_BYTES
    if file.stat().st_size > maximum:
        raise ImportValidationError("导入文件超出格式大小限制")
    return preview({"fileName": file.name, "contentBase64": base64.b64encode(file.read_bytes()).decode()})
