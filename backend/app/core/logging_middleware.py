"""Centralized request/response logging middleware for MedHistry API.

Logs every incoming request and outgoing response in a clean, colour-coded
console format for easy debugging.  Includes:
  - HTTP method + path + query string
  - Client IP
  - Request body (truncated for large payloads)
  - Response status code + timing
  - Response body preview (truncated)
"""

import time
import json
import logging
from typing import Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response, StreamingResponse
from starlette.types import Message

logger = logging.getLogger("medhistry.http")

# ANSI colour helpers
CYAN = "\033[96m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
RED = "\033[91m"
MAGENTA = "\033[95m"
DIM = "\033[2m"
RESET = "\033[0m"
BOLD = "\033[1m"

MAX_BODY_LOG = 2000  # max chars to log for request/response bodies


def _status_color(status: int) -> str:
    if status < 300:
        return GREEN
    elif status < 400:
        return YELLOW
    elif status < 500:
        return RED
    return MAGENTA


def _truncate(text: str, limit: int = MAX_BODY_LOG) -> str:
    if len(text) <= limit:
        return text
    return text[:limit] + f"... ({len(text)} chars total)"


def _pretty_json(text: str) -> str:
    """Try to pretty-print JSON; return raw text on failure."""
    try:
        obj = json.loads(text)
        return json.dumps(obj, indent=2, ensure_ascii=False)
    except (json.JSONDecodeError, TypeError):
        return text


class RequestResponseLogger(BaseHTTPMiddleware):
    """Middleware that logs every HTTP request and response to the console."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        # ---- Request phase ----
        start = time.perf_counter()
        method = request.method
        path = request.url.path
        query = str(request.url.query)
        client_ip = request.client.host if request.client else "unknown"

        # Read request body (if present)
        req_body = ""
        if method in ("POST", "PUT", "PATCH"):
            try:
                raw = await request.body()
                req_body = raw.decode("utf-8", errors="replace")
            except Exception:
                req_body = "<could not read body>"

        url_display = path + (f"?{query}" if query else "")

        print(
            f"\n{BOLD}{CYAN}>>> {method} {url_display}{RESET}"
            f"  {DIM}from {client_ip}{RESET}"
        )
        if req_body:
            pretty = _pretty_json(req_body)
            print(f"{DIM}    Body: {_truncate(pretty)}{RESET}")

        # Log request headers in debug mode
        headers_to_log = {
            k: v for k, v in request.headers.items()
            if k.lower() in ("content-type", "authorization", "user-agent", "x-request-id")
        }
        if headers_to_log:
            # Mask authorization token
            if "authorization" in headers_to_log:
                val = headers_to_log["authorization"]
                if len(val) > 20:
                    headers_to_log["authorization"] = val[:15] + "..."
            print(f"{DIM}    Headers: {headers_to_log}{RESET}")

        # ---- Response phase ----
        # Collect response body by intercepting the streaming response
        response_body_parts = []

        async def receive_body(message: Message):
            if message.get("type") == "http.response.body":
                body = message.get("body", b"")
                if body:
                    response_body_parts.append(body)

        try:
            response = await call_next(request)
        except Exception as exc:
            elapsed = (time.perf_counter() - start) * 1000
            print(
                f"{RED}<<< {method} {url_display} "
                f"EXCEPTION in {elapsed:.1f}ms: {exc}{RESET}\n"
            )
            raise

        elapsed = (time.perf_counter() - start) * 1000
        status = response.status_code
        color = _status_color(status)

        # Read and re-stream the response body
        resp_body = b""
        if hasattr(response, "body_iterator"):
            chunks = []
            async for chunk in response.body_iterator:
                if isinstance(chunk, str):
                    chunk = chunk.encode("utf-8")
                chunks.append(chunk)
            resp_body = b"".join(chunks)

            # Rebuild response with the same body
            from starlette.responses import Response as StarletteResponse
            response = StarletteResponse(
                content=resp_body,
                status_code=response.status_code,
                headers=dict(response.headers),
                media_type=response.media_type,
            )

        resp_text = resp_body.decode("utf-8", errors="replace") if resp_body else ""

        # Skip logging large HTML responses in detail
        content_type = response.headers.get("content-type", "")
        is_html = "text/html" in content_type

        print(
            f"{color}{BOLD}<<< {status}{RESET} "
            f"{color}{method} {url_display}{RESET}"
            f"  {DIM}{elapsed:.1f}ms{RESET}"
        )

        if resp_text and not is_html:
            pretty = _pretty_json(resp_text)
            print(f"{DIM}    Response: {_truncate(pretty)}{RESET}")
        elif is_html:
            print(f"{DIM}    Response: <HTML {len(resp_text)} chars>{RESET}")

        print()  # blank line separator

        return response
