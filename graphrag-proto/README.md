# GraphRAG 原型 · 《双峰谐振》

ProjVault 引入"图谱增强检索"的预研。先在单篇文档上把 GraphRAG 的核心机制跑通、看清效果，再决定如何增量集成进 PKC。

## 它做了什么

```
text.docx ──分块──► chunks ──(LLM/缓存)抽取──► 实体 + 关系
        ──► networkx 图 ──► 社区检测(greedy_modularity) ──► 社区摘要
查询：
  naive  关键词命中 chunk → 拼上下文            （基线，对照组）
  local  命中实体 → 邻域子图 → 关系+来源文本     （细节/关系类问题强）
  global 遍历社区摘要 map-reduce                （宏观/主题类问题强）
```

当前图谱规模：**31 实体 / 41 关系 / 5 社区 / 17 文本单元**。社区自动聚成了故事的四条叙事线：
主角技术因果线、未来自己的抉择线、林舒—谐振线、戒指—绝对零度悬念线（外加一个孤点 年轻技术员）。

## 文件

| 文件 | 说明 |
|---|---|
| `graphrag_proto.py` | 主程序（建图 / 问答 / 对比 / demo） |
| `extraction.json` | 实体+关系+文本单元（可由 LLM 重生成，此处为高质量缓存，使无 Key 也能跑通） |
| `graph.html` | 交互式知识图谱（vis-network，按社区着色，悬停看关系，**双击打开**） |
| `communities.json` | 社区划分与摘要 |
| `demo_qa.md` | 朴素RAG vs Local vs Global 的答案对比（看 GraphRAG 价值） |

## 运行

```bash
# 建图 + 社区 + 导出可视化（无需任何 Key）
python graphrag_proto.py build

# 提问（三种模式）
python graphrag_proto.py ask "沈墨和林舒是什么关系？" --mode local
python graphrag_proto.py ask "这篇故事讲了什么？"       --mode global
python graphrag_proto.py compare "戒指为什么是绝对零度？"   # 一题三模式对比
python graphrag_proto.py demo                             # 预设问题集
```

**接真实模型**（让答案由 LLM 生成、抽取也可实时跑）——设置环境变量即可，零改代码：

```bash
export LLM_API_KEY=sk-你的key
export LLM_BASE_URL=https://api.deepseek.com/v1   # 默认值，可改 OpenAI/Qwen/Ollama
export LLM_MODEL=deepseek-chat                     # 默认值
python graphrag_proto.py build --use-llm           # LLM 实时抽取
python graphrag_proto.py compare "..."             # 答案由模型生成
```

> 无 Key 时：抽取走缓存，问答输出"已检索到的图谱上下文"（即喂给模型的料），证明检索机制本身正确，最终润色留给真实模型。本机已用 DeepSeek 验证可端到端生成答案。

## 对应 GraphRAG 概念（与你分享的资料一一对照）

- **Local search**：以问题命中的实体为种子，沿图谱邻域扩展，汇集"实体描述 + 关系 + 来源文本单元"作上下文。擅长**关系类/细节类**问题（"沈墨和林舒什么关系""跛脚老人是谁"）——朴素 RAG 因答案散落多个 chunk 而难拼全。
- **Global search**：对每个社区摘要做 map（判相关、抽要点）再 reduce（汇总）。擅长**宏观/主题类**问题（"整篇讲了什么""有哪些时空节点")——朴素 RAG 受 top-k 截断、几乎答不全。
- **DRIFT**：动态混合（先 Global 定位相关社区，再在社区内做 Local 细化）。本原型未实现，集成阶段可加。

### 针对你提到的 7 个坑，本原型的应对

1. **多路召回拉不开差距** → 保留 naive 基线做 A/B，`compare` 命令一眼看差异。
2. **图结构相关≠问题相关** → Local 以"问题命中实体"为种子，而非全图最高 PageRank。
3. **命中节点≠命中原文** → 每个实体绑定 `text_units`，检索结果**始终回带原文**（白盒可溯源）。
4. **Hybrid 融合引噪声** → 当前不盲目融合；Local/Global 分治，由问题类型选路（后续可加路由器）。
5. **问题设计** → `demo` 内置关系类/宏观类两组代表问题。
6. **评测误导** → 不依赖单一自动分，强调人工对照 `demo_qa.md` + 可视化核查。
7. **无法回答→分数虚低** → 提示词要求"无依据明确说明、不编造"。

## 集成进 ProjVault 的路线（达到预期后）

1. **抽取阶段**：在扫描流水线 P4/P5 之后增 **P-Entity**，对 chunk 调 `ExtractModelProvider` 产出实体/关系，落 `pkc_graph_node(type=ENTITY)` + `pkc_graph_edge`（复用现有图谱表，零新依赖）。
2. **图谱融合**：ENTITY 节点与现有 FILE/DIR/SERVICE 图谱合并，边带 `sourceChunkId` 支持白盒溯源（呼应已做的"引用查看原文"）。
3. **检索增强**：`ChunkRetriever` 现有 BM25-lite 之上加 **Local 邻域扩展**——命中 chunk→其实体→邻居实体的 chunk 一并召回。
4. **问答模式**：`/ask` 增 `mode=local|global`，Global 复用"社区/版本族摘要"。
5. **可视化**：前端"知识图谱"面板叠加 ENTITY 层 + 检索路径高亮。
6. 向量召回（等 Ollama `nomic-embed-text` 配好）再叠加，与图谱邻域互补 = 完整 Local search。
