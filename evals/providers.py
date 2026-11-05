"""Model providers beyond Inspect's built-ins. Imported by every eval task.

- codex/<model>: OpenAI models through the Codex SDK and your ChatGPT login
  (see codex_provider.py).
- openai-api/ntnu/<model>: NTNU's LLM servers (IDUN, llm.hpc.ntnu.no), an
  OpenAI-compatible API. Defaults to the local tunnel on 127.0.0.1:8742,
  which needs no key. Set NTNU_BASE_URL / NTNU_API_KEY in .env to call
  https://llm.hpc.ntnu.no/v1 directly instead.
"""

import os
import sys
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")
os.environ.setdefault("NTNU_BASE_URL", "http://127.0.0.1:8742/v1")
os.environ.setdefault("NTNU_API_KEY", "local-tunnel")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import codex_provider  # noqa: E402,F401  (registers the codex provider)
