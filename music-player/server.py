#!/usr/bin/env python3
"""Slimo Mūzika — savs serveris (neobligāts, bet visdrošākais avots).

Palaišana:
    pip install -U yt-dlp
    python server.py            # klausās uz 0.0.0.0:8000

Tad telefonā atver http://<datora-IP>:8000 (tajā pašā Wi-Fi) — aplikācija
izmantos šo serveri automātiski. Vai arī ieraksti šo adresi aplikācijas
iestatījumos laukā "Savs serveris".

API:
    GET /api/search?q=...&type=music|video  -> JSON saraksts
    GET /api/audio/<id>                     -> audio straume (ar Range atbalstu)
    GET /api/video/<id>                     -> mp4 video straume
"""
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

try:
    import yt_dlp
except ImportError:
    sys.exit("Vajag yt-dlp:  pip install -U yt-dlp")

ROOT = os.path.dirname(os.path.abspath(__file__))
PORT = int(os.environ.get("PORT", "8000"))
ID_RE = re.compile(r"^[\w-]{11}$")
_cache = {}  # (id, kind) -> (time, url, headers)
_lock = threading.Lock()


def ydl(opts=None):
    base = {"quiet": True, "no_warnings": True, "skip_download": True}
    base.update(opts or {})
    return yt_dlp.YoutubeDL(base)


def search(q, kind):
    query = q if kind == "video" else f"{q} audio"
    with ydl({"extract_flat": True}) as y:
        info = y.extract_info(f"ytsearch25:{query}", download=False)
    out = []
    for e in info.get("entries") or []:
        vid = e.get("id")
        if not vid or not ID_RE.match(vid):
            continue
        out.append({
            "id": vid,
            "title": e.get("title") or "",
            "artist": (e.get("channel") or e.get("uploader") or "").removesuffix(" - Topic"),
            "duration": e.get("duration") or 0,
        })
    return out


def resolve(vid, kind):
    key = (vid, kind)
    with _lock:
        hit = _cache.get(key)
        if hit and time.time() - hit[0] < 3 * 3600:
            return hit[1], hit[2]
    fmt = "bestaudio[ext=m4a]/bestaudio" if kind == "audio" else "best[ext=mp4][vcodec!=none][acodec!=none]/best"
    with ydl({"format": fmt}) as y:
        info = y.extract_info(f"https://www.youtube.com/watch?v={vid}", download=False)
    url = info["url"]
    headers = info.get("http_headers") or {}
    with _lock:
        _cache[key] = (time.time(), url, headers)
    return url, headers


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        u = urlparse(self.path)
        if u.path == "/api/search":
            qs = parse_qs(u.query)
            q = (qs.get("q") or [""])[0].strip()
            kind = (qs.get("type") or ["music"])[0]
            try:
                data = json.dumps(search(q, kind) if q else []).encode()
            except Exception as e:  # noqa: BLE001
                return self.send_error(502, str(e)[:200])
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        m = re.match(r"^/api/(audio|video)/([\w-]{11})$", u.path)
        if m:
            return self.stream(m.group(2), m.group(1))
        if u.path.startswith("/server.py"):
            return self.send_error(404)
        return super().do_GET()

    def stream(self, vid, kind, retried=False):
        try:
            url, headers = resolve(vid, kind)
        except Exception as e:  # noqa: BLE001
            return self.send_error(502, str(e)[:200])
        h = dict(headers)
        if self.headers.get("Range"):
            h["Range"] = self.headers["Range"]
        try:
            resp = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=20)
        except urllib.error.HTTPError as e:
            if e.code in (403, 410) and not retried:
                _cache.pop((vid, kind), None)  # saite novecojusi
                return self.stream(vid, kind, True)
            return self.send_error(502, f"upstream {e.code}")
        except Exception as e:  # noqa: BLE001
            return self.send_error(502, str(e)[:200])
        with resp:
            self.send_response(resp.status)
            for k in ("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"):
                if resp.headers.get(k):
                    self.send_header(k, resp.headers[k])
            if not resp.headers.get("Accept-Ranges"):
                self.send_header("Accept-Ranges", "bytes")
            self.end_headers()
            try:
                while chunk := resp.read(64 * 1024):
                    self.wfile.write(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass


if __name__ == "__main__":
    print(f"Slimo Mūzika serveris: http://0.0.0.0:{PORT}  (Ctrl+C lai apturētu)")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
