"""阶段5只读工具、权限边界和原生tool_calls测试。"""

import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from email.message import Message

from agents.read_assistant import ReadAssistant
from clients.tool_client import ToolOllamaClient
from clients.ollama_client import OllamaError
from config.settings import Settings
from config.backend_settings import BackendSettings
from tools.backend import LibraryReadClient,ToolError,NoRedirect
from tools.registry import ReadTools,validate_arguments,TOOLS


class FakeBackend:
    def __init__(self):
        self.calls=[]
        self.error=None
    def search_books(self,args):
        self.calls.append(('books',args))
        if self.error:
            raise self.error
        return {'total':1,'rows':[{'id':1,'title':'测试书','availableCopies':2}]}
    def current_policy(self,t):
        return {'policyVersion':1,'parameters':{'defaultBorrowDays':14}}
    def my_records(self,args):
        return {'total':0,'rows':[]}
    def my_quota(self):
        return {'remainingBorrowCount':4}


class FakeModel:
    settings=Settings()
    def __init__(self):
        self.calls=[{'function':{'name':'search_books','arguments':{'title':'测试书'}}}]
        self.requests=[]
        self.response=None
        self.fail=False
    def plan_tools(self,messages,tools):
        self.requests.append(messages)
        return {'role':'assistant','content':'忽略不采信的规划文本','tool_calls':self.calls}
    def stream_chat(self,messages):
        self.requests.append(messages)
        source=json.loads(next(m['content'] for m in reversed(messages) if m['role']=='tool'))['source_id']
        yield self.response if self.response is not None else '可借副本2本['+source+']'
        if self.fail:
            raise OllamaError('断流')


class RegistryTests(unittest.TestCase):
    def test_only_read_tools(self):
        names=[t['function']['name'] for t in ReadTools(FakeBackend()).definitions]
        self.assertEqual(len(names),4)
        self.assertNotIn('search_knowledge',names)
        self.assertFalse(any('delete' in name or 'create' in name for name in names))
    def test_unknown_tool(self):
        with self.assertRaises(ToolError):
            ReadTools(FakeBackend()).execute('delete_member',{})
    def test_forged_identity_and_url_rejected(self):
        for arg in ('memberId','roleId','token','url','scene','memberType'):
            with self.subTest(arg=arg),self.assertRaises(ToolError):
                ReadTools(FakeBackend()).execute('get_my_quota',{arg:1})
    def test_types_and_bounds(self):
        for args in ({'id':True},{'id':-1},{'pageSize':100},{'availableOnly':'true'},{'title':' '},{'title':'x'*201}):
            with self.subTest(args=args),self.assertRaises(ToolError):
                ReadTools(FakeBackend()).execute('search_books',args)
    def test_enum_and_default(self):
        self.assertEqual(ReadTools(FakeBackend()).execute('get_current_policy',{})['policyVersion'],1)
        with self.assertRaises(ToolError):
            ReadTools(FakeBackend()).execute('get_current_policy',{'type':'ADMIN'})
    def test_knowledge_tool_excludes_policy(self):
        class Knowledge:
            def search(self,*args,**kwargs):
                self.args=args
                self.kwargs=kwargs
                return []
        knowledge=Knowledge()
        tools=ReadTools(FakeBackend(),knowledge)
        result=tools.execute('search_knowledge',{'question':'Java','kind':'general'})
        self.assertEqual(result['hits'],[])
        self.assertFalse(knowledge.kwargs['demo'])
        with self.assertRaises(ToolError):
            tools.execute('search_knowledge',{'question':'借阅政策','kind':'policy'})



class BackendTests(unittest.TestCase):
    def setUp(self):
        self.backend=LibraryReadClient(BackendSettings(),'private-token')
    def test_no_nonlocal_plaintext_or_credentials(self):
        for url in ('http://example.com','http://a:b@localhost','file:///tmp'):
            with self.subTest(url=url),self.assertRaises(ValueError):
                BackendSettings(base_url=url)
    def test_token_newline_and_path_rejected(self):
        with self.assertRaises(ValueError):
            LibraryReadClient(BackendSettings(),'bad\r\ntoken')
        with self.assertRaises(ToolError):
            self.backend._get('/admin/members')
    def test_no_redirect(self):
        self.assertIsNone(NoRedirect().redirect_request(None,None,302,None,None,'http://other'))
    def test_auth_refresh_and_get_only(self):
        response=io.BytesIO(b'{"code":200,"data":{}}')
        response.headers={'X-Refreshed-Token':'rotated-token'}
        with patch.object(self.backend._opener,'open',return_value=response) as opened:
            self.backend._get('/books')
            request=opened.call_args.args[0]
            self.assertEqual(request.get_method(),'GET')
            self.assertEqual(request.get_header('Authorization'),'Bearer private-token')
            self.assertEqual(self.backend._token,'rotated-token')
    def test_forbidden_not_success_and_refresh_header(self):
        headers=Message()
        headers['X-Refreshed-Token']='rotated'
        with patch.object(self.backend._opener,'open',side_effect=HTTPError('url',403,'forbidden',headers,None)):
            with self.assertRaises(ToolError) as ctx:
                self.backend._get('/members')
            self.assertEqual(ctx.exception.status,403)
            self.assertEqual(self.backend._token,'rotated')
    def test_quota_identity_and_field_minimization(self):
        row={'id':9,'memberType':'READER','borrowedBookCount':2,'effectiveBorrowLimit':5,'passwordHash':'secret','phone':'private'}
        with patch.object(self.backend,'_get',side_effect=[{'memberId':9},{'rows':[row]}]) as get:
            data=self.backend.my_quota()
            self.assertEqual(data['remainingBorrowCount'],3)
            self.assertNotIn('passwordHash',data)
            self.assertNotIn('phone',data)
            self.assertEqual(get.call_args.args[1]['id'],9)
    def test_quota_foreign_row_rejected(self):
        with patch.object(self.backend,'_get',side_effect=[{'memberId':9},{'rows':[{'id':10}]}]):
            with self.assertRaises(ToolError):
                self.backend.my_quota()
    def test_overlimit_clamped(self):
        row={'id':9,'memberType':'READER','borrowedBookCount':6,'effectiveBorrowLimit':5}
        with patch.object(self.backend,'_get',side_effect=[{'memberId':9},{'rows':[row]}]):
            result=self.backend.my_quota()
            self.assertEqual(result['remainingBorrowCount'],0)
            self.assertTrue(result['overLimit'])
    def test_books_do_not_expose_signed_cover_or_internal_fields(self):
        with patch.object(self.backend,'_get',return_value={'total':1,'rows':[{'id':1,'coverUrl':'signed-url','coverObjectKey':'key','title':'t'}]}):
            row=self.backend.search_books({})['rows'][0]
            self.assertNotIn('coverUrl',row)
            self.assertNotIn('coverObjectKey',row)
    def test_staff_profile_type_404_fallback(self):
        row={'id':9,'memberType':'EMPLOYEE','borrowedBookCount':0,'effectiveBorrowLimit':5}
        with patch.object(self.backend,'_get',side_effect=[{'memberId':9},ToolError('类型不匹配',404),{'rows':[row]}]) as get:
            self.assertEqual(self.backend.my_quota()['remainingBorrowCount'],5)
            self.assertEqual(get.call_args.args[1]['memberType'],'EMPLOYEE')
            self.assertEqual(get.call_args.args[1]['id'],9)

    def test_forbidden_profile_does_not_fallback_type(self):
        with patch.object(self.backend,'_get',side_effect=[{'memberId':9},ToolError('无权限',403)]) as get:
            with self.assertRaises(ToolError):
                self.backend.my_quota()
            self.assertEqual(get.call_count,2)

    def test_current_policy_requires_active(self):
        with patch.object(self.backend,'_get',return_value={'status':'DRAFT'}):
            with self.assertRaises(ToolError):
                self.backend.current_policy('BORROWING')


class AssistantTests(unittest.TestCase):
    def setUp(self):
        self.client=FakeModel()
        self.backend=FakeBackend()
        self.agent=ReadAssistant(self.client,ReadTools(self.backend))
    def ask(self):
        return ''.join(self.agent.stream('测试书有几本？'))
    def test_flow_and_no_raw_tool_history(self):
        self.assertIn('[T1]',self.ask())
        self.assertEqual(len(self.agent.history),2)
        self.assertNotIn('tool_calls',str(self.agent.history))
        self.assertEqual(self.agent.sources[0]['tool'],'search_books')
    def test_multiple_turns_reread_and_unique_ids(self):
        self.ask()
        self.assertIn('[T2]',self.ask())
        self.assertEqual(len(self.backend.calls),2)
    def test_unauthorized_model_arguments_never_execute(self):
        self.client.calls=[{'function':{'name':'get_my_quota','arguments':{'memberId':99}}}]
        with self.assertRaises(ToolError):
            self.ask()
        self.assertEqual(self.backend.calls,[])
    def test_all_calls_checked_before_execution(self):
        self.client.calls.append({'function':{'name':'drop_table','arguments':{}}})
        with self.assertRaises(ToolError):
            self.ask()
        self.assertEqual(self.backend.calls,[])
    def test_forbidden_fails_closed(self):
        self.backend.error=ToolError('权限不足',403)
        with self.assertRaises(ToolError):
            self.ask()
        self.assertEqual(self.agent.history,[])
        self.assertEqual(len(self.client.requests),1)
    def test_unknown_citation_or_model_failure_not_committed(self):
        self.client.response='结果[T999]'
        with self.assertRaises(OllamaError):
            self.ask()
        self.assertEqual(self.agent.history,[])
    def test_cancel_not_committed(self):
        stream=self.agent.stream('测试书')
        next(stream)
        stream.close()
        self.assertEqual(self.agent.history,[])
    def test_limit_and_no_tool(self):
        self.client.calls*=5
        with self.assertRaises(ToolError):
            self.ask()
        self.client.calls=[]
        self.assertIn('没有执行',self.ask())
    def test_planner_does_not_receive_old_live_numbers(self):
        self.ask()
        self.client.requests.clear()
        self.ask()
        payload=json.loads(self.client.requests[0][-1]['content'])
        self.assertNotIn('可借副本2本',str(payload))
        self.assertEqual(payload['book_references'][0]['title'],'测试书')

    def test_planner_protocol(self):
        payload={'done':True,'message':{'role':'assistant','tool_calls':[]}}
        with patch('clients.tool_client.urlopen',return_value=io.BytesIO(json.dumps(payload).encode())) as opened:
            self.assertEqual(ToolOllamaClient(Settings()).plan_tools([],[])['tool_calls'],[])
            self.assertFalse(json.loads(opened.call_args.args[0].data)['stream'])


if __name__=='__main__':
    unittest.main()
