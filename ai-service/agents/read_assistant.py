"""只读工具问答：原生tool_calls规划，严格白名单执行，流式组织结果。"""

from copy import deepcopy
import json
from pathlib import Path
import re

from clients.ollama_client import OllamaError
from tools.backend import ToolError
from tools.registry import validate_arguments
from agents.dialogue import CHAT_TOOL, should_chat, stream_dialogue


class ReadAssistant:
    def __init__(self,client,tools):
        self.client=client
        self.tools=tools
        self.prompt=(Path(__file__).resolve().parents[1]/'prompts/read_tools.md').read_text(encoding='utf-8')
        self._history=[]
        self._sources=[]
        self._next_source=1

    @property
    def history(self):
        return deepcopy(self._history)

    @property
    def sources(self):
        return deepcopy(self._sources)

    def clear(self):
        self._history.clear()
        self._sources.clear()
        self._next_source=1

    def stream(self,question):
        if not isinstance(question,str) or not question.strip() or len(question)>2000:
            raise ValueError('业务咨询问题需1～2000字符')
        keep=self.client.settings.max_turns-1
        history=self.history[-2*keep:] if keep else []
        base_size=len(self.prompt)+len(question)
        budget=(self.client.settings.context-self.client.settings.max_tokens)//2
        while history and base_size+sum(len(m['content']) for m in history)>budget//2:
            history=history[2:]
        if base_size>budget//2:
            raise ValueError('问题超过当前业务工具上下文预算')
        messages=[{'role':'system','content':self.prompt},*history,{'role':'user','content':question}]
        # 规划阶段不提供历史助手结论/实时数值，避免模型直接复用旧库存和额度。
        # 只保留用户问题与上次真实结果中的书目定位信息，供指代消解。
        book_refs=[]
        for source in self._sources:
            data=source['data']
            if isinstance(data,dict):
                for row in data.get('rows',[]):
                    if isinstance(row,dict) and row.get('title'):
                        book_refs.append({'id':row.get('bookId',row.get('id')),'title':row['title']})
        planning_payload={'question':question,
                          'recent_user_questions':[m['content'] for m in history if m['role']=='user'],
                          'book_references':book_refs}
        plan_messages=[{'role':'system','content':self.prompt+'\n工具规划只依据最后JSON中的question选择工具；book_references只帮助定位书目，不代表当前库存。'},
                       {'role':'user','content':json.dumps(planning_payload,ensure_ascii=False)}]
        plan=self.client.plan_tools(plan_messages,[*self.tools.definitions, CHAT_TOOL])
        calls=plan.get('tool_calls',[])
        if not isinstance(calls,list) or len(calls)>4:
            raise ToolError('单轮工具调用数量超出限制')
        sources=[]
        if should_chat(calls, question):
            parts=[]
            for text in stream_dialogue(self.client,self.prompt,history,question):
                parts.append(text)
                yield text
            answer=''.join(parts)
        elif not calls:
            answer='本轮没有执行可用的只读查询。请说明要查询的书目、本人借阅记录、额度或政策；当前不能执行业务修改。'
            yield answer
        else:
            validated=[]
            # 先校验整轮调用，模型只能提供白名单业务参数。
            for call in calls:
                function=call.get('function') if isinstance(call,dict) else None
                if not isinstance(function,dict):
                    raise ToolError('模型工具调用格式错误')
                name,arguments=function.get('name'),function.get('arguments',{})
                definition=next((d for d in self.tools.definitions if d['function']['name']==name),None)
                if definition is None:
                    raise ToolError('模型请求了未开放工具')
                if isinstance(arguments,str):
                    try:
                        arguments=json.loads(arguments)
                    except ValueError as exc:
                        raise ToolError('工具参数不是有效JSON') from exc
                validate_arguments(definition['function']['parameters'],arguments)
                validated.append({'function':{'name':name,'arguments':arguments}})
            messages.append({'role':'assistant','content':'','tool_calls':validated})
            for call in validated:
                function=call['function']
                result=self.tools.execute(function['name'],function['arguments'])
                # 不缓存工具结果；401/403等错误直接终止本轮，不能让模型改走其他接口。
                source={'source_id':'T'+str(self._next_source+len(sources)),
                        'tool':function['name'],'data':result}
                encoded=json.dumps(source,ensure_ascii=False)
                if len(encoded)>10000:
                    raise ToolError('工具结果过大，请缩小查询范围')
                sources.append(source)
                messages.append({'role':'tool','tool_name':function['name'],'content':encoded})
            if sum(len(m.get('content','')) for m in messages)>budget:
                raise ToolError('工具结果超过上下文预算，请缩小查询范围')
            parts=[]
            for text in self.client.stream_chat(messages):
                parts.append(text)
                yield text
            answer=''.join(parts)
            citations={'T'+n for n in re.findall(r'\[T(\d+)\]',answer)}
            allowed={s['source_id'] for s in sources}
            if not answer.strip() or not citations or not citations.issubset(allowed):
                raise OllamaError('业务回答缺少本轮有效引用，本轮未保存')
        self._history=history+[{'role':'user','content':question},{'role':'assistant','content':answer}]
        self._sources=sources
        self._next_source+=len(sources)
