"""Inspect model provider that runs OpenAI models through the Codex SDK.

    uv run inspect eval evals/scam_eval.py@scam_detection --model codex/gpt-6-luna

Uses the local Codex login (`codex login`, your ChatGPT account), so no
OpenAI API key is needed. Each generate() call is one ephemeral, read-only
Codex thread: the system message becomes the thread's base instructions and
the rest of the conversation is sent as one text turn.

Limits:
- No custom tools. Tasks that give the model tools (safe_word) can't run here.
- Codex adds its own tool definitions (~18k input tokens) to every request,
  so token counts and latency are higher than a bare API call would be.
- Billed to the ChatGPT plan, not per token.

Importing this module registers the `codex` provider with Inspect.
"""

from __future__ import annotations

import asyncio
import tempfile
from typing import Any

from inspect_ai.model import (
    ChatMessage,
    ChatMessageSystem,
    GenerateConfig,
    ModelAPI,
    ModelOutput,
    ModelUsage,
    modelapi,
)
from inspect_ai.tool import ToolChoice, ToolInfo

# Inspect reasoning_effort -> Codex effort. "none" keeps it closest to the
# board, which turns thinking off.
_EFFORT = {
    "none": "none",
    "minimal": "minimal",
    "low": "low",
    "medium": "medium",
    "high": "high",
    "xhigh": "xhigh",
    "max": "max",
}

_codex: Any = None
_codex_lock = asyncio.Lock()


async def _client():
    global _codex
    async with _codex_lock:
        if _codex is None:
            from openai_codex import AsyncCodex

            _codex = AsyncCodex()
            await _codex.__aenter__()
    return _codex


def _render(messages: list[ChatMessage]) -> str:
    if len(messages) == 1:
        return messages[0].text
    return "\n\n".join(f"[{m.role}]\n{m.text}" for m in messages)


@modelapi(name="codex")
class CodexAPI(ModelAPI):
    def __init__(
        self,
        model_name: str,
        base_url: str | None = None,
        api_key: str | None = None,
        config: GenerateConfig = GenerateConfig(),
        **model_args: Any,
    ) -> None:
        super().__init__(model_name=model_name, base_url=base_url, api_key=api_key, config=config)
        self._cwd = tempfile.mkdtemp(prefix="codex-eval-")

    def max_connections(self) -> int:
        return 4

    async def generate(
        self,
        input: list[ChatMessage],
        tools: list[ToolInfo],
        tool_choice: ToolChoice,
        config: GenerateConfig,
    ) -> ModelOutput:
        if tools:
            raise RuntimeError("codex provider does not support custom tools")
        from openai_codex import ApprovalMode, Sandbox

        system = "\n\n".join(m.text for m in input if isinstance(m, ChatMessageSystem)) or None
        rest = [m for m in input if not isinstance(m, ChatMessageSystem)]

        codex = await _client()
        thread = await codex.thread_start(
            model=self.model_name,
            base_instructions=system,
            ephemeral=True,
            sandbox=Sandbox.read_only,
            approval_mode=ApprovalMode.deny_all,
            cwd=self._cwd,
        )
        effort = _EFFORT.get(config.reasoning_effort or "", None)
        result = await thread.run(_render(rest), effort=effort)
        if result.error is not None or result.final_response is None:
            raise RuntimeError(f"codex turn {result.status}: {result.error}")

        usage = None
        if result.usage is not None:
            u = result.usage.total
            usage = ModelUsage(
                input_tokens=u.input_tokens - u.cached_input_tokens,
                input_tokens_cache_read=u.cached_input_tokens,
                output_tokens=u.output_tokens,
                reasoning_tokens=u.reasoning_output_tokens,
                total_tokens=u.total_tokens,
            )
        output = ModelOutput.from_content(self.model_name, result.final_response)
        output.usage = usage
        if result.duration_ms is not None:
            output.time = result.duration_ms / 1000
        return output
