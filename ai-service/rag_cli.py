"""知识导入与检索入口；不生成回答，也不执行Java业务。"""

import argparse
import json
import sys
from dataclasses import replace
from pathlib import Path

from clients.ollama_client import OllamaError
from config.rag_settings import RagSettings
from config.settings import Settings
from rag.embedding import OllamaEmbedding
from rag.store import KnowledgeStore, format_sources
from rag.service import KnowledgeService


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, help="知识库独立目录，默认项目data/rag")
    sub = parser.add_subparsers(dest="command", required=True)
    ingest = sub.add_parser("ingest", help="按清单导入整个知识快照")
    ingest.add_argument("manifest", type=Path)
    ingest.add_argument("--expected-generation", help="预期快照版本，冲突时拒绝更新；空字符串表示首次导入")
    sub.add_parser("status", help="查询当前知识快照版本")
    query = sub.add_parser("query", help="只查询已发布快照中的有效知识")
    query.add_argument("question")
    query.add_argument("--kind", choices=("policy", "general", "book_intro"))
    query.add_argument("--policy-id")
    query.add_argument("--version")
    query.add_argument("--demo", action="store_true", help="仅查询测试资料，默认排除")
    query.add_argument("--top-k", type=int)
    query.add_argument("--min-similarity", type=float)
    query.add_argument("--json", action="store_true")
    args = parser.parse_args()
    try:
        settings = RagSettings.from_env()
        if args.data_dir is not None:
            settings = replace(settings, data_dir=args.data_dir)
        model = Settings.from_env()
        embedding = OllamaEmbedding(model.base_url, settings.embedding_model, model.timeout)
        service = KnowledgeService(KnowledgeStore(settings, embedding))
        if args.command == "ingest":
            print(json.dumps(service.update_knowledge(args.manifest, expected_generation=args.expected_generation), ensure_ascii=False, indent=2))
        elif args.command == "status":
            print(json.dumps(service.get_status(), ensure_ascii=False, indent=2))
        else:
            hits = service.search(args.question, kind=args.kind, policy_id=args.policy_id,
                               version=args.version, demo=args.demo, top_k=args.top_k,
                               min_similarity=args.min_similarity)
            print(json.dumps(hits, ensure_ascii=False, indent=2) if args.json else format_sources(hits) or "没有检索到符合条件的知识；不能据此猜测答案。")
        return 0
    except (OllamaError, ValueError, OSError) as exc:
        print("RAG错误：" + str(exc), file=sys.stderr)
        return 1
    except Exception as exc:
        # Chroma底层异常也给出可诊断信息，不伪装为空检索结果。
        print(f"知识库操作失败（{type(exc).__name__}）：{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
