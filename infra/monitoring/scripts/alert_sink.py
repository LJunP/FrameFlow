"""Local-only Alertmanager webhook sink used to prove alert delivery without SaaS."""

from __future__ import annotations

import json
import os
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

_LOCK = threading.Lock()
_ALERTS: list[dict] = []


class Handler(BaseHTTPRequestHandler):
    server_version = "FrameFlowAlertSink/1.0"

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path == "/health":
            self._json(200, {"status": "UP"})
            return
        if self.path == "/alerts":
            with _LOCK:
                payload = {"count": len(_ALERTS), "alerts": list(_ALERTS[-20:])}
            self._json(200, payload)
            return
        self._json(404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path != "/alerts":
            self._json(404, {"error": "not_found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 1_048_576:
                raise ValueError("payload size outside 1..1048576")
            document = json.loads(self.rfile.read(length))
        except (ValueError, json.JSONDecodeError) as exc:
            self._json(400, {"error": "invalid_payload", "detail": str(exc)})
            return
        evidence = {
            "receivedAt": datetime.now(timezone.utc).isoformat(),
            "environment": os.environ.get("FRAMEFLOW_ENV", "unknown"),
            "payload": document,
        }
        with _LOCK:
            _ALERTS.append(evidence)
            del _ALERTS[:-100]
        print(json.dumps({"event": "alert_received", **evidence}, ensure_ascii=False), flush=True)
        self._json(202, {"accepted": True})

    def log_message(self, format: str, *args: object) -> None:
        return

    def _json(self, status: int, payload: dict) -> None:
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main() -> None:
    port = int(os.environ.get("FRAMEFLOW_ALERT_SINK_PORT", "5001"))
    if not 1 <= port <= 65535:
        raise SystemExit("FRAMEFLOW_ALERT_SINK_PORT must be 1..65535")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
