"""Render the project design as a readable, local-only companion page."""
from pathlib import Path
import re, html
root=Path(__file__).parent
text=(root.parent.parent/'architecture/ROUND-7-IMPLEMENTATION-PLAN.md').read_text(encoding='utf-8')
(root/'design.md').write_text(text,encoding='utf-8')
def inline(s):
 s=html.escape(s)
 s=re.sub(r'`([^`]+)`',r'<code>\1</code>',s)
 return re.sub(r'\*\*([^*]+)\*\*',r'<strong>\1</strong>',s)
out=[];table=False;listing=False
for line in text.splitlines():
 if not line.startswith('|') and table: out.append('</tbody></table></div>');table=False
 islist=line.startswith('- ') or bool(re.match(r'^\d+\. ',line))
 if listing and not islist:out.append('</ul>');listing=False
 if line.startswith('|'):
  cells=[x.strip() for x in line.strip('|').split('|')]
  if all(re.fullmatch(r'[:\- ]+',c) for c in cells):continue
  if not table:out.append('<div class="table"><table><tbody>');table=True
  out.append('<tr>'+''.join('<td>'+inline(c)+'</td>' for c in cells)+'</tr>')
 elif line.startswith('#'):
  n=min(len(line)-len(line.lstrip('#')),6)
  out.append(f'<h{n}>'+inline(line[n:].strip())+f'</h{n}>')
 elif islist:
  if not listing:out.append('<ul>');listing=True
  out.append('<li>'+inline(re.sub(r'^(?:- |\d+\. )','',line))+'</li>')
 elif line.strip():out.append('<p>'+inline(line)+'</p>')
if table:out.append('</tbody></table></div>')
if listing:out.append('</ul>')
page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>详细方案 / 异域来物</title><style>body{max-width:1060px;margin:0 auto;padding:40px 6vw 100px;background:#101b1e;color:#dddcc7;font:15px/2 "Microsoft YaHei",sans-serif}a{color:#d7e779}h1,h2,h3{font-family:Georgia,"SimSun",serif;font-weight:400;line-height:1.5}h1{font-size:34px;margin:50px 0}h2{color:#d7e779;border-top:1px solid #354845;padding-top:30px;margin-top:50px}h3{font-size:20px;color:#d9bc7b}p,li{color:#afc1b8}strong{color:#ece7cc}code{font:12px Consolas,monospace;background:#283733;padding:3px 5px}.table{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}td{padding:13px;border:1px solid #354845;min-width:120px;vertical-align:top}tr:first-child td{color:#d7e779;background:#263630}nav{display:flex;justify-content:space-between;font-size:12px}li{margin:8px 0}</style><nav><a href="/">← 返回模型展台</a><a href="design.md" download>下载 Markdown 源文档 ↓</a></nav>'''+''.join(out)+'</html>'
(root/'design.html').write_text(page,encoding='utf-8')
print('Built design.html and design.md')
