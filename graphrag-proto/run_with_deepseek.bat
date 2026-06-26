@echo off
chcp 65001 >nul
cd /d %~dp0
echo ============================================================
echo  GraphRAG 原型 · 双峰谐振 · 接 DeepSeek 真实模型
echo ============================================================

REM 找 Python
where python >nul 2>nul && (set PY=python) || (where py >nul 2>nul && (set PY=py) || (
  echo [错误] 未找到 Python，请先安装 Python 3 并加入 PATH。
  pause & exit /b 1))

REM 装依赖（已装会秒过）
%PY% -m pip install --quiet python-docx networkx 2>nul

REM Key：优先用已有环境变量，否则当场输入（只留在本机，不外传）
if "%LLM_API_KEY%"=="" set /p LLM_API_KEY=请粘贴 DeepSeek API Key（sk-...）然后回车:
set LLM_BASE_URL=https://api.deepseek.com/v1
set LLM_MODEL=deepseek-v4-pro

echo.
echo [1/3] 建图 + 真实社区摘要（用精校图谱，社区摘要由 DeepSeek 生成）...
%PY% graphrag_proto.py build
echo （想看 LLM 自动抽取实体的效果，可另跑：%PY% graphrag_proto.py build --use-llm）
echo.
echo [2/3] 三模式对比：朴素RAG vs Local vs Global
%PY% graphrag_proto.py compare "跛脚老人到底是谁？给出推理链。"
echo.
echo [3/3] 预设问题集（真实答案）
%PY% graphrag_proto.py demo
echo.
echo 完成。可自行追问，例如：
echo   %PY% graphrag_proto.py ask "戒指为什么是绝对零度？" --mode local
echo   %PY% graphrag_proto.py ask "整篇讲了什么？" --mode global
pause
