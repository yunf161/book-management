"""文件预览、来源持久化和真实索引测试；PDF由pypdf生成，不依赖外部文件或模型。"""

import base64
from dataclasses import replace
import io
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

from pypdf import PdfWriter
from pypdf.generic import DictionaryObject, NameObject, DecodedStreamObject

from config.rag_settings import RagSettings
from rag.importing import ImportValidationError, parse_file, preview, validate_metadata
from rag.documents import load_manifest
from rag.management import KnowledgeManagement, ManagementError
from rag.service import KnowledgeService
from rag.store import KnowledgeStore
from tests.test_stage3 import FakeEmbedding


def make_pdf(texts, encrypted=False):
    writer = PdfWriter()
    for text in texts:
        page = writer.add_blank_page(width=300, height=300)
        if text:
            font = DictionaryObject({NameObject("/Type"): NameObject("/Font"),
                                     NameObject("/Subtype"): NameObject("/Type1"),
                                     NameObject("/BaseFont"): NameObject("/Helvetica")})
            page[NameObject("/Resources")] = DictionaryObject({NameObject("/Font"): DictionaryObject({
                NameObject("/F1"): writer._add_object(font)})})
            stream = DecodedStreamObject()
            safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            stream.set_data(f"BT /F1 12 Tf 30 250 Td ({safe}) Tj ET".encode("ascii"))
            page[NameObject("/Contents")] = writer._add_object(stream)
    if encrypted:
        writer.encrypt("test-only-password")
    output = io.BytesIO()
    writer.write(output)
    return output.getvalue()


class ImportingTests(unittest.TestCase):
    def test_local_manifest_also_accepts_csv_json_pdf_with_locations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            documents = []
            for name, raw in [("a.csv", b"title\nJava"), ("b.json", b'{"title":"SQL"}'),
                              ("c.pdf", make_pdf(["PDF source"]))]:
                (root / name).write_bytes(raw)
                documents.append(dict(id=name,file=name,title=name,source="test://local",kind="general",
                                      version="v1",status="ACTIVE",demo=False))
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(dict(documents=documents)))
            rows, _ = load_manifest(manifest, RagSettings())
            self.assertEqual({row["metadata"]["import_format"] for row in rows}, {"csv","json","pdf"})
            self.assertTrue(all(row["metadata"]["source_locations"] for row in rows))

    def test_md_txt_utf8_bom_and_empty_invalid_encoding(self):
        result = parse_file("说明.MD", "\ufeff  中文正文\r\n下一行  ".encode())
        self.assertEqual(result["content"], "中文正文\n下一行")
        for data in (b"", b"  ", b"\xff", b"\x00abc"):
            with self.subTest(data=data), self.assertRaises(ImportValidationError):
                parse_file("a.txt", data)

    def test_csv_keeps_headers_quotes_multiline_and_physical_row_number(self):
        result = parse_file("books.csv", '书名,简介\nJava,"第一行\n第二行"\nSQL,数据库\n'.encode())
        self.assertIn("书名: Java", result["content"])
        self.assertIn("简介: 第一行\n第二行", result["content"])
        self.assertEqual([s["locator"] for s in result["importMetadata"]["sections"]], ["第2行", "第4行"])

    def test_csv_invalid_headers_columns_and_empty_records(self):
        for data in ('a,a\n1,2', ',b\n1,2', 'a,b\n1', 'a\n"unterminated', 'a,b\n'):
            with self.subTest(data=data), self.assertRaises(ImportValidationError):
                parse_file("bad.csv", data.encode())

    def test_json_preserves_keys_depth_and_object_positions(self):
        result = parse_file("books.json", '[{"书名":"Java","作者":{"姓名":"甲"}},{"书名":"SQL"}]'.encode())
        self.assertIn('"姓名": "甲"', result["content"])
        self.assertEqual([s["locator"] for s in result["importMetadata"]["sections"]], ["$[0]", "$[1]"])
        self.assertEqual(parse_file("object.json", b'{"b":2,"a":1}')["content"],
                         parse_file("other.json", b'{"a":1,"b":2}')["content"])

    def test_json_rejects_jsonl_primitives_duplicates_nan_depth_and_invalid_unicode(self):
        values = ['{}', '[]', '[1]', '42', '{"a":1}\n{"a":2}', '{"a":1,"a":2}',
                  '{"a":NaN}', '{"a":1e999}', '{"a":"\\ud800"}', '[' * 15 + '1' + ']' * 15]
        for value in values:
            with self.subTest(value=value), self.assertRaises(ImportValidationError):
                parse_file("bad.json", value.encode())

    def test_pdf_extracts_pages_and_warns_about_blank_pages(self):
        result = parse_file("guide.pdf", make_pdf(["First page library", "", "Third page books"]))
        self.assertEqual(result["units"], 3)
        self.assertEqual([s["locator"] for s in result["importMetadata"]["sections"]], ["第1页", "第3页"])
        self.assertEqual(len(result["warnings"]), 1)
        self.assertIn("First page library", result["content"])

    def test_pdf_rejects_scan_empty_encrypted_malformed_and_too_many_pages(self):
        for data in (b"bad", b"%PDF-1.7\nbad", make_pdf([""]), make_pdf(["text"], encrypted=True), make_pdf([""] * 101)):
            with self.subTest(length=len(data)), self.assertRaises(ImportValidationError):
                parse_file("bad.pdf", data)

    def test_limits_extensions_and_metadata_edits(self):
        for name, data in (("../file.md", b"x"), ("file.docx", b"x"), ("file.txt", b"a" * 600001),
                           ("file.txt", b"a" * 200001), ("file.pdf", b"a" * (4 * 1024 * 1024 + 1))):
            with self.subTest(name=name, size=len(data)), self.assertRaises(ImportValidationError):
                parse_file(name, data)
        parsed = parse_file("file.json", b'{"title":"test"}')
        self.assertIsNotNone(validate_metadata(parsed["importMetadata"], parsed["content"]))
        with self.assertRaises(ImportValidationError):
            validate_metadata(parsed["importMetadata"], parsed["content"] + "changed")

    def test_real_isolated_worker_and_invalid_base64(self):
        parsed = preview({"fileName": "isolated.pdf", "contentBase64": base64.b64encode(make_pdf(["Isolated worker"])).decode()})
        self.assertIn("Isolated worker", parsed["content"])
        with self.assertRaises(ImportValidationError):
            preview({"fileName": "bad.json", "contentBase64": "@@@"})

    def test_timeout_and_busy_worker_do_not_block_ai(self):
        import subprocess
        import rag.importing as importing
        body = {"fileName":"test.txt", "contentBase64":"dGVzdA=="}
        with patch("rag.importing.subprocess.run", side_effect=subprocess.TimeoutExpired("test",15)):
            with self.assertRaisesRegex(ImportValidationError,"15秒"):
                preview(body)
        importing.PARSING_SLOTS.acquire()
        importing.PARSING_SLOTS.acquire()
        try:
            with self.assertRaises(ImportValidationError) as error:
                preview(body)
            self.assertEqual(error.exception.status,503)
        finally:
            importing.PARSING_SLOTS.release()
            importing.PARSING_SLOTS.release()


class ImportIndexTests(unittest.TestCase):
    def test_manager_initializes_catalog_from_structured_manifest(self):
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as directory:
            root = Path(directory)
            (root / "source.pdf").write_bytes(make_pdf(["Initialization source page"]))
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps({"documents":[dict(id="pdf",file="source.pdf",title="PDF",
                source="test://init",kind="general",version="v1",status="ACTIVE",demo=False)]}))
            store = KnowledgeStore(replace(RagSettings(),data_dir=root / "index"), FakeEmbedding())
            manager = KnowledgeManagement(KnowledgeService(store), manifest)
            try:
                self.assertEqual(manager.document("pdf")["importMetadata"]["format"], "pdf")
                row = manager.documents({})["rows"][0]
                self.assertNotIn("importMetadata", row)
                self.assertEqual(row["sourceUnits"], 1)
            finally:
                manager.close()

    def test_preview_save_md5_rebuild_locations_and_search(self):
        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as directory:
            store = KnowledgeStore(replace(RagSettings(),data_dir=Path(directory)), FakeEmbedding())
            manager = KnowledgeManagement(KnowledgeService(store))
            try:
                for name, raw in [("book.csv",b"title,author\nJava,Author"),
                                  ("book.json",b'{"title":"SQL"}'),
                                  ("book.pdf",make_pdf(["Library books guidance"]))]:
                    parsed = parse_file(name,raw)
                    body = dict(title=name,source="test://import",kind="general",version="v1",status="ACTIVE",
                                content=parsed["content"],importMetadata=parsed["importMetadata"])
                    saved = manager.save(body)
                    self.assertEqual(saved["importMetadata"],parsed["importMetadata"])
                    with self.assertRaises(ManagementError):
                        manager.save(body)
                status = manager.status()
                job = manager.rebuild(dict(expectedRevision=status["revision"],expectedGeneration=status["snapshot"]["generation"]))
                for _ in range(100):
                    final = manager.job(job["id"])
                    if final["status"] in ("SUCCEEDED","FAILED"):
                        break
                    time.sleep(.02)
                self.assertEqual(final["status"],"SUCCEEDED",final)
                hits = store.query("Library",top_k=20,min_similarity=0)
                self.assertEqual({h["metadata"]["import_format"] for h in hits},{"csv","json","pdf"})
                self.assertTrue(all(json.loads(h["metadata"]["source_locations"]) for h in hits))
            finally:
                manager.close()
