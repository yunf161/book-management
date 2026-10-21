"""Java只读业务助手入口；使用已有会话令牌，不在模型中执行登录。"""

import argparse
from dataclasses import replace
import getpass
import os
from pathlib import Path
import sys

from agents.read_assistant import ReadAssistant
from clients.model_client import create_model_client
from config.backend_settings import BackendSettings
from config.settings import Settings
from tools.backend import LibraryReadClient
from tools.registry import ReadTools


def answer(session,question):
    for text in session.stream(question):
        print(text,end='',flush=True)
    print()
    for source in session.sources:
        print(f"[{source['source_id']}] 只读来源：{source['tool']}")


def main():
    for output in (sys.stdout,sys.stderr):
        if hasattr(output,'reconfigure'):
            output.reconfigure(encoding='utf-8',errors='replace')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('question',nargs='?')
    parser.add_argument('--token-prompt',action='store_true',help='隐藏输入业务系统当前会话令牌')
    parser.add_argument('--api-url')
    parser.add_argument('--knowledge-dir',type=Path,help='可选正式通用知识库目录；不从本地政策回答现行规则')
    parser.add_argument('--model')
    parser.add_argument('--max-tokens',type=int)
    args=parser.parse_args()
    try:
        backend=BackendSettings.from_env()
        if args.api_url:
            backend=replace(backend,base_url=args.api_url)
        runtime=Settings.from_env()
        if args.model:
            runtime=replace(runtime,model=args.model)
        if args.max_tokens is not None:
            runtime=replace(runtime,max_tokens=args.max_tokens)
        token=getpass.getpass('当前业务会话令牌（隐藏输入）：') if args.token_prompt else os.getenv('LIBRARY_ACCESS_TOKEN','')
        knowledge=None
        if args.knowledge_dir is not None:
            from config.rag_settings import RagSettings
            from rag.embedding import OllamaEmbedding
            from rag.service import KnowledgeService
            from rag.store import KnowledgeStore
            rag=replace(RagSettings.from_env(),data_dir=args.knowledge_dir)
            knowledge=KnowledgeService(KnowledgeStore(rag,OllamaEmbedding(runtime.base_url,rag.embedding_model,runtime.timeout)))
        session=ReadAssistant(create_model_client(runtime),ReadTools(LibraryReadClient(backend,token),knowledge))
    except (ValueError,EOFError) as exc:
        print(f'配置失败：{exc}',file=sys.stderr)
        return 1
    if args.question is not None:
        try:
            answer(session,args.question)
            return 0
        except KeyboardInterrupt:
            return 130
        except Exception as exc:
            print(f'查询失败：{exc}；部分输出不作为有效答案，本轮未保存。',file=sys.stderr)
            return 1
    print('Java只读业务助手。/clear清空历史，/exit退出。查询个人数据需要有效业务会话。')
    while True:
        try:
            question=input('\n你：').strip()
            if question=='/exit':
                return 0
            if question=='/clear':
                session.clear()
                print('历史已清空。')
                continue
            if not question:
                continue
            if question.startswith('/'):
                print('支持/clear、/exit。')
                continue
            print('助手：',end='',flush=True)
            answer(session,question)
        except EOFError:
            return 0
        except KeyboardInterrupt:
            print('\n已取消，本轮未保存。')
        except Exception as exc:
            print(f'查询失败：{exc}；部分输出不作为有效答案，本轮未保存。',file=sys.stderr)


if __name__=='__main__':
    sys.exit(main())
