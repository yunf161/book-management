"""只保存模型返回的计量数据，不记录提示词、密钥和工具结果。"""

def record(client, provider, model, value):
    prompt = value.get("prompt_tokens", value.get("prompt_eval_count"))
    completion = value.get("completion_tokens", value.get("eval_count"))
    if type(prompt) is not int or type(completion) is not int or prompt < 0 or completion < 0:
        return
    if not hasattr(client, "usage"):
        client.usage = []
    client.usage.append({"provider": provider, "model": model, "promptTokens": prompt, "completionTokens": completion})
