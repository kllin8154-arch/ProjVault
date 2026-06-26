#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GraphRAG 原型（ProjVault 预研）
=================================
在单篇文档《双峰谐振》上演示 GraphRAG 的核心机制，并与朴素 RAG 对比。

流水线：
  docx --(分块)--> chunks --(LLM 抽取/缓存)--> 实体+关系 --> networkx 图
       --> 社区检测(Leiden 近似: greedy_modularity) --> 社区摘要
  查询:
    - naive  : 关键词命中 chunk 直接拼上下文（基线）
    - local  : 命中实体 -> 邻域子图 -> 关联关系+文本单元 -> 上下文（细节/关系类问题强）
    - global : 遍历社区摘要 map-reduce（宏观/主题类问题强）

LLM 可选：设置环境变量即走真实模型（OpenAI 兼容，如 DeepSeek）：
    export LLM_API_KEY=sk-xxx
    export LLM_BASE_URL=https://api.deepseek.com/v1   # 默认
    export LLM_MODEL=deepseek-chat                     # 默认
无 Key 时：抽取走缓存 extraction.json；摘要/答案给出"检索上下文 + 结构化结论"，
          流程依然端到端跑通（演示检索机制本身，最终润色留给真实模型）。

用法：
    python graphrag_proto.py build                 # 建图 + 社区 + 导出可视化
    python graphrag_proto.py ask "沈墨和林舒是什么关系？" --mode local
    python graphrag_proto.py ask "这篇故事到底讲了什么？" --mode global
    python graphrag_proto.py compare "戒指为什么是绝对零度？"   # 三种模式对比
    python graphrag_proto.py demo                  # 跑一组预设问题
"""
import os, sys, json, re, argparse, urllib.request
from pathlib import Path

BASE = Path(__file__).resolve().parent
EXTRACTION = BASE / "extraction.json"
DOCX = BASE.parent.parent / "Project-Knowledge-Center" / "text.docx"  # 可选，仅 LLM 抽取时用

# ---------------------------------------------------------------- LLM (可选)
def llm(system, user, max_tokens=1400):
    key = os.environ.get("LLM_API_KEY", "").strip()
    if not key:
        return None  # 无 key -> 调用方走结构化回退
    base = os.environ.get("LLM_BASE_URL", "https://api.deepseek.com/v1").rstrip("/")
    model = os.environ.get("LLM_MODEL", "deepseek-chat")
    body = json.dumps({
        "model": model,
        "messages": [{"role": "system", "content": system},
                     {"role": "user", "content": user}],
        "max_tokens": max_tokens, "temperature": 0.3,
    }).encode("utf-8")
    req = urllib.request.Request(base + "/chat/completions", data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + key})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            data = json.loads(r.read().decode("utf-8"))
        return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"[llm] 调用失败，回退结构化输出: {e}", file=sys.stderr)
        return None

# ---------------------------------------------------------------- 抽取
def extract_from_docx_via_llm():
    """演示用：真实 GraphRAG 的抽取阶段。无 key 时不调用（直接用缓存）。"""
    from docx import Document
    paras = [p.text for p in Document(str(DOCX)).paragraphs if p.text.strip()]
    text = "\n".join(paras)
    # 简单按字符窗口分块
    chunks, size, ov = [], 1200, 150
    i = 0
    while i < len(text):
        chunks.append(text[i:i+size]); i += size - ov
    sys_p = ("你是知识图谱抽取器。从中文小说片段中抽取实体与关系，只输出JSON："
             '{"entities":[{"name","type","desc"}],"relations":[{"source","target","type","desc","weight"}]}。'
             "type用中文（人物/实物/地点/概念/事件/频率/信号/系统等）。不要输出多余文字。")
    ents, rels = {}, []
    for c in chunks:
        out = llm(sys_p, c, max_tokens=1500)
        if not out:
            continue
        m = re.search(r"\{.*\}", out, re.S)
        if not m:
            continue
        try:
            j = json.loads(m.group(0))
        except Exception:
            continue
        for e in j.get("entities", []):
            if e.get("name"):
                ents.setdefault(e["name"], e)
        rels.extend(j.get("relations", []))
    return {"doc": "双峰谐振", "entities": list(ents.values()), "relations": rels, "text_units": []}

def load_extraction(use_llm=False):
    if use_llm and os.environ.get("LLM_API_KEY"):
        print("[extract] 使用 LLM 实时抽取 ...")
        data = extract_from_docx_via_llm()
        (BASE / "extraction_llm.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        return data
    return json.loads(EXTRACTION.read_text(encoding="utf-8"))

# ---------------------------------------------------------------- 建图
def build_graph(data):
    import networkx as nx
    G = nx.Graph()
    for e in data["entities"]:
        G.add_node(e["name"], type=e.get("type", ""), desc=e.get("desc", ""))
    for r in data["relations"]:
        s, t = r["source"], r["target"]
        if s not in G or t not in G:
            continue
        if G.has_edge(s, t):
            G[s][t]["rels"].append(r)
            G[s][t]["weight"] = max(G[s][t]["weight"], r.get("weight", 0.5))
        else:
            G.add_edge(s, t, weight=r.get("weight", 0.5), rels=[r])
    # 文本单元映射：实体 -> [text_unit]
    units = data.get("text_units", [])
    ent2units = {}
    for u in units:
        for en in u.get("entities", []):
            ent2units.setdefault(en, []).append(u)
    return G, units, ent2units

def detect_communities(G):
    from networkx.algorithms.community import greedy_modularity_communities
    comms = list(greedy_modularity_communities(G, weight="weight"))
    node2comm = {}
    for i, c in enumerate(comms):
        for n in c:
            node2comm[n] = i
    return comms, node2comm

def community_summaries(G, comms, node2comm):
    """每个社区一段摘要。有 LLM 走真实摘要，否则结构化拼装。"""
    summaries = []
    for i, c in enumerate(comms):
        members = sorted(c, key=lambda n: G.degree(n, weight="weight"), reverse=True)
        # 收集社区内关系
        rel_lines = []
        seen = set()
        for n in members:
            for nb in G.neighbors(n):
                if nb in c and (nb, n) not in seen:
                    seen.add((n, nb))
                    for r in G[n][nb]["rels"]:
                        rel_lines.append(f"{r['source']} --[{r['type']}]--> {r['target']}：{r['desc']}")
        ent_lines = [f"- {n}（{G.nodes[n]['type']}）：{G.nodes[n]['desc']}" for n in members]
        ctx = "实体：\n" + "\n".join(ent_lines) + "\n\n关系：\n" + "\n".join(rel_lines)
        s = llm("你是社区摘要器。把以下知识图谱社区压缩成150字内中文主题摘要，突出该社区讲了什么、核心实体与关系。",
                ctx, max_tokens=600)
        if not s:
            top = "、".join(members[:5])
            s = f"[结构化摘要] 核心实体：{top}。共 {len(members)} 个实体、{len(rel_lines)} 条关系。代表关系：" + \
                ("；".join(rel_lines[:3]) if rel_lines else "（无社区内关系）")
        summaries.append({"id": i, "members": members, "n_rel": len(rel_lines), "summary": s})
    return summaries

# ---------------------------------------------------------------- 检索
def seed_entities(G, question):
    """问题中命中的实体（按名字子串），补充与命中实体高频共现的别名。"""
    hits = [n for n in G.nodes if n in question or any(tok and tok in question for tok in re.split(r"[（）()/·]", n) if len(tok) >= 2)]
    # 关键词兜底：把问题里 >=2 字的词与实体 desc 匹配
    if not hits:
        for n in G.nodes:
            if any(w in G.nodes[n]["desc"] for w in re.findall(r"[一-鿿]{2,}", question)):
                hits.append(n)
    return list(dict.fromkeys(hits))

def local_context(G, ent2units, question, hops=1, max_units=8):
    seeds = seed_entities(G, question)
    if not seeds:
        return seeds, "（未命中任何实体，Local 检索为空）"
    sub = set(seeds)
    for _ in range(hops):
        for n in list(sub):
            sub.update(G.neighbors(n))
    # 排序：与种子的连接强度
    sub = sorted(sub, key=lambda n: (n in seeds, G.degree(n, weight="weight")), reverse=True)
    ent_lines, rel_lines, unit_set = [], [], []
    for n in sub:
        ent_lines.append(f"- {n}（{G.nodes[n]['type']}）：{G.nodes[n]['desc']}")
        for u in ent2units.get(n, []):
            if u["id"] not in [x["id"] for x in unit_set]:
                unit_set.append(u)
    seen = set()
    for n in seeds:
        for nb in G.neighbors(n):
            key = tuple(sorted((n, nb)))
            if key in seen:
                continue
            seen.add(key)
            for r in G[n][nb]["rels"]:
                rel_lines.append(f"{r['source']} --[{r['type']}]--> {r['target']}：{r['desc']}")
    units = unit_set[:max_units]
    ctx = ("【命中实体及邻域】\n" + "\n".join(ent_lines[:15]) +
           "\n\n【相关关系】\n" + "\n".join(rel_lines[:15]) +
           "\n\n【来源文本单元】\n" + "\n".join(f"({u['id']} {u['chapter']}) {u['text']}" for u in units))
    return seeds, ctx

def naive_context(units, question, k=3):
    """朴素 RAG 基线：按关键词命中数排序取 top-k 文本单元。"""
    kws = re.findall(r"[一-鿿]{2,}", question)
    scored = []
    for u in units:
        s = sum(u["text"].count(w) for w in kws)
        scored.append((s, u))
    scored.sort(key=lambda x: x[0], reverse=True)
    top = [u for s, u in scored if s > 0][:k] or [u for s, u in scored][:k]
    return "【Top-{} 文本单元(按关键词)】\n".format(k) + "\n".join(
        f"({u['id']} {u['chapter']}) {u['text']}" for u in top)

# ---------------------------------------------------------------- 问答
def answer_local(G, ent2units, question):
    seeds, ctx = local_context(G, ent2units, question)
    a = llm("你是项目文档助手。只依据提供的图谱上下文回答，中文，简洁准确，可说明实体间关系链。",
            f"上下文：\n{ctx}\n\n问题：{question}", max_tokens=1400)
    return seeds, ctx, a

def answer_global(summaries, question):
    # map
    partials = []
    for c in summaries:
        a = llm("依据该社区摘要，判断它对回答问题是否有用，有用则给出要点(不超过60字)，无用回复'无关'。",
                f"社区摘要：{c['summary']}\n问题：{question}", max_tokens=320)
        if a and "无关" not in a:
            partials.append(f"[社区{c['id']}] {a}")
        elif not a:
            partials.append(f"[社区{c['id']}] {c['summary']}")
    # reduce
    red = llm("综合各社区要点，给出对问题的整体中文回答，结构清晰。",
              "问题：" + question + "\n\n各社区要点：\n" + "\n".join(partials), max_tokens=1500)
    return partials, red

def answer_naive(units, question):
    ctx = naive_context(units, question)
    a = llm("你是文档助手，只依据片段回答，中文。", f"片段：\n{ctx}\n\n问题：{question}", max_tokens=1200)
    return ctx, a

# ---------------------------------------------------------------- 可视化导出
def export_viz(G, node2comm, out_html):
    import json as _j
    palette = ["#5eead4","#f0abfc","#fcd34d","#93c5fd","#fca5a5","#86efac","#c4b5fd","#fdba74","#67e8f9","#f9a8d4"]
    nodes = []
    for n in G.nodes:
        c = node2comm.get(n, 0)
        deg = G.degree(n, weight="weight")
        nodes.append({"id": n, "label": n, "group": c,
                      "title": f"{G.nodes[n]['type']}｜社区{c}<br>{G.nodes[n]['desc']}",
                      "value": 6 + deg * 4, "color": palette[c % len(palette)]})
    edges = []
    for s, t, d in G.edges(data=True):
        lbl = d["rels"][0]["type"] if d.get("rels") else ""
        tip = "<br>".join(f"{r['source']}→{r['target']}（{r['type']}）：{r['desc']}" for r in d.get("rels", []))
        edges.append({"from": s, "to": t, "label": lbl, "title": tip,
                      "width": 1 + d.get("weight", 0.5) * 3})
    html = VIZ_TEMPLATE.replace("__NODES__", _j.dumps(nodes, ensure_ascii=False)) \
                       .replace("__EDGES__", _j.dumps(edges, ensure_ascii=False)) \
                       .replace("__NCOMM__", str(max(node2comm.values()) + 1 if node2comm else 1))
    Path(out_html).write_text(html, encoding="utf-8")

VIZ_TEMPLATE = r"""<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<title>双峰谐振 · GraphRAG 知识图谱</title>
<script src="https://cdnjs.cloudflare.com/ajax/libs/vis-network/9.1.6/dist/vis-network.min.js"></script>
<style>
:root{--bg:#0b1220;--surface:#121a2b;--ink:#e6edf6;--muted:#8aa0b8;}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.5 system-ui,"Microsoft YaHei",sans-serif}
header{padding:14px 18px;border-bottom:1px solid #1e2a40;display:flex;align-items:center;gap:14px;flex-wrap:wrap}
h1{font-size:16px;margin:0;font-weight:600}
.tag{color:var(--muted);font-size:12px}
#net{height:calc(100vh - 58px);width:100%}
.legend{position:fixed;right:14px;top:64px;background:rgba(18,26,43,.92);border:1px solid #1e2a40;border-radius:10px;padding:10px 12px;max-width:230px;font-size:12px}
.legend b{display:block;margin-bottom:6px;color:var(--ink)}
.dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:6px;vertical-align:middle}
</style></head><body>
<header><h1>《双峰谐振》知识图谱</h1>
<span class="tag">实体按社区着色 · 边标注关系 · 滚轮缩放 · 拖拽 · 悬停看详情</span></header>
<div id="net"></div>
<div class="legend"><b>社区（共 __NCOMM__ 个）</b><div id="leg"></div>
<div style="margin-top:8px;color:var(--muted)">节点大小=加权度数</div></div>
<script>
const nodes=new vis.DataSet(__NODES__), edges=new vis.DataSet(__EDGES__);
const pal=["#5eead4","#f0abfc","#fcd34d","#93c5fd","#fca5a5","#86efac","#c4b5fd","#fdba74","#67e8f9","#f9a8d4"];
const net=new vis.Network(document.getElementById('net'),{nodes,edges},{
 nodes:{shape:'dot',font:{color:'#e6edf6',size:14,face:'Microsoft YaHei'},borderWidth:0},
 edges:{color:{color:'#3a4d6e',highlight:'#5eead4'},font:{color:'#8aa0b8',size:11,strokeWidth:0,align:'middle'},
        smooth:{type:'continuous'},arrows:{to:{enabled:false}}},
 physics:{barnesHut:{gravitationalConstant:-9000,springLength:150,springConstant:0.03},stabilization:{iterations:220}},
 interaction:{hover:true,tooltipDelay:120}});
const leg=document.getElementById('leg');
for(let i=0;i<__NCOMM__;i++){const d=document.createElement('div');
 d.innerHTML='<span class="dot" style="background:'+pal[i%pal.length]+'"></span>社区 '+i;leg.appendChild(d);}
</script></body></html>"""

# ---------------------------------------------------------------- CLI
def cmd_build(args):
    data = load_extraction(use_llm=args.use_llm)
    G, units, ent2units = build_graph(data)
    comms, node2comm = detect_communities(G)
    summ = community_summaries(G, comms, node2comm)
    out_html = BASE / "graph.html"
    export_viz(G, node2comm, out_html)
    (BASE / "communities.json").write_text(
        json.dumps(summ, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"实体 {G.number_of_nodes()}　关系边 {G.number_of_edges()}　社区 {len(comms)}　文本单元 {len(units)}")
    for c in summ:
        print(f"  社区{c['id']}({len(c['members'])}实体,{c['n_rel']}关系): {c['summary'][:60]}…")
    print(f"可视化 -> {out_html}")
    return G, units, ent2units, summ

def _prep():
    data = load_extraction(use_llm=False)
    G, units, ent2units = build_graph(data)
    comms, node2comm = detect_communities(G)
    summ = community_summaries(G, comms, node2comm)
    return G, units, ent2units, summ

def cmd_ask(args):
    G, units, ent2units, summ = _prep()
    if args.mode == "local":
        seeds, ctx, a = answer_local(G, ent2units, args.question)
        print(f"== LOCAL ==\n命中种子实体: {seeds}\n")
        print(a if a else "[无 LLM Key] 已检索到以下图谱上下文，交给模型即可生成答案：\n\n" + ctx)
    elif args.mode == "global":
        partials, red = answer_global(summ, args.question)
        print("== GLOBAL ==")
        if red:
            print(red)
        else:
            print("[无 LLM Key] 各社区命中要点（map 阶段）：\n" + "\n".join(partials))
    else:
        ctx, a = answer_naive(units, args.question)
        print("== NAIVE ==\n" + (a if a else ctx))

def cmd_compare(args):
    G, units, ent2units, summ = _prep()
    q = args.question
    print("问题：" + q + "\n" + "=" * 60)
    ctx_n, a_n = answer_naive(units, q)
    print("\n[1] 朴素RAG：\n" + (a_n if a_n else ctx_n))
    seeds, ctx_l, a_l = answer_local(G, ent2units, q)
    print("\n[2] GraphRAG-Local（种子=%s）：\n" % seeds + (a_l if a_l else ctx_l))
    partials, red = answer_global(summ, q)
    print("\n[3] GraphRAG-Global：\n" + (red if red else "\n".join(partials)))

DEMO_Q = [
    ("沈墨和林舒是什么关系？", "local"),
    ("跛脚老人到底是谁？给出推理链。", "local"),
    ("戒指为什么会是绝对零度？", "local"),
    ("这篇故事整体讲了一个什么故事？", "global"),
    ("故事里有哪些时间/空间节点，它们怎么连接？", "global"),
]
def cmd_demo(args):
    G, units, ent2units, summ = _prep()
    for q, m in DEMO_Q:
        print("\n" + "#" * 64 + f"\nQ（{m}）：{q}")
        if m == "local":
            seeds, ctx, a = answer_local(G, ent2units, q)
            print(f"种子实体：{seeds}")
            print(a if a else "[检索上下文已就绪，待模型生成]\n" + ctx)
        else:
            partials, red = answer_global(summ, q)
            print(red if red else "[map 命中]\n" + "\n".join(partials))

if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="GraphRAG 原型 · 双峰谐振")
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build"); b.add_argument("--use-llm", action="store_true")
    k = sub.add_parser("ask"); k.add_argument("question"); k.add_argument("--mode", default="local", choices=["naive","local","global"])
    c = sub.add_parser("compare"); c.add_argument("question")
    sub.add_parser("demo")
    args = ap.parse_args()
    {"build": cmd_build, "ask": cmd_ask, "compare": cmd_compare, "demo": cmd_demo}[args.cmd](args)
