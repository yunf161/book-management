"""RAG咨询入口：python consult_cli.py；--demo只使用演示知识。"""

import argparse
import sys
from dataclasses import replace
from pathlib import Path

from agents.consultation import ConsultationSession, format_consultation_sources
from clients.model_client import create_model_client
from config.rag_settings import RagSettings
from config.settings import Settings
from rag.embedding import OllamaEmbedding
from rag.service import KnowledgeService
from rag.store import KnowledgeStore


def answer(session, question):
    for text in session.stream(question):
        print(text, end="", flush=True)
    print()
    if session.sources:
        print("\n本轮检索来源（来源存在不等于回答已被自动证实）：")
        print(format_consultation_sources(session.sources))


def main():
    for output in (sys.stdout, sys.stderr):
        if hasattr(output, "reconfigure"):
            output.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("question", nargs="?")
    parser.add_argument("--data-dir", type=Path)
    parser.add_argument("--demo", action="store_true")
    parser.add_argument("--model")
    parser.add_argument("--url")
    parser.add_argument("--context", type=int)
    parser.add_argument("--timeout", type=float)
    parser.add_argument("--max-tokens", type=int)
    args = parser.parse_args()
    try:
        overrides = {key: value for key, value in vars(args).items()
                     if key not in ("question", "data_dir", "demo", "url") and value is not None}
        if args.url is not None:
            overrides["base_url"] = args.url
        runtime = replace(Settings.from_env(), **overrides)
        rag = RagSettings.from_env()
        if args.data_dir is not None:
            rag = replace(rag, data_dir=args.data_dir)
        knowledge = KnowledgeService(KnowledgeStore(
            rag, OllamaEmbedding(runtime.base_url, rag.embedding_model, runtime.timeout)))
        session = ConsultationSession(create_model_client(runtime), knowledge, demo=args.demo)
    except Exception as exc:
        print(f"初始化失败：{exc}", file=sys.stderr)
        return 1
    if args.question is not None:
        try:
            answer(session, args.question)
            return 0
        except KeyboardInterrupt:
            print("\n已取消，本轮未保存。", file=sys.stderr)
            return 130
        except Exception as exc:
            print(f"\n咨询失败：{exc}。已显示的部分输出不作为有效答案，本轮未保存。", file=sys.stderr)
            return 1
    print(f"RAG咨询：{runtime.model}，上下文{runtime.context}，模式：{'测试资料' if args.demo else '正式资料'}。")
    print("/sources 查看上个成功回答的检索来源，/clear 清空，/exit 退出。")
    while True:
        try:
            question = input("\n你：").strip()
            if question == "/exit":
                return 0
            if question == "/clear":
                session.clear()
                print("历史和来源已清空。")
                continue
            if question == "/sources":
                print(format_consultation_sources(session.sources) or "上个成功回答没有检索来源。")
                continue
            if not question:
                continue
            if question.startswith("/"):
                print("支持 /sources、/clear、/exit。")
                continue
            print("助手：", end="", flush=True)
            answer(session, question)
        except EOFError:
            return 0
        except KeyboardInterrupt:
            print("\n已取消，本轮未保存，可继续提问。")
        except Exception as exc:
            print(f"\n咨询失败：{exc}。已显示的部分输出不作为有效答案，本轮未保存。", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main())
