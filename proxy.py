#!/usr/bin/env python3
"""
HomeRemote ADB Proxy
Run this on your PC to enable D-pad control for the Mi Box.
Requires: ADB connected to the TV (adb connect YOUR_TV_IP:5555)
Usage:    python proxy.py
"""
import subprocess
import json
from http.server import HTTPServer, BaseHTTPRequestHandler

TV      = "YOUR_TV_IP:5555"
ADB     = r"C:\Users\Linda\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PORT    = 9191

KEYS = {
    "DPAD_UP":     19,
    "DPAD_DOWN":   20,
    "DPAD_LEFT":   21,
    "DPAD_RIGHT":  22,
    "DPAD_CENTER": 23,
    "BACK":         4,
    "HOME":         3,
    "MENU":       187,
    "ENTER":       66,
    "DEL":         67,
}

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            body   = json.loads(self.rfile.read(length))
            key    = body.get("key", "")
            if key in KEYS:
                subprocess.Popen(
                    [ADB, "-s", TV, "shell", "input", "keyevent", str(KEYS[key])],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
                print(f"  keyevent {key} ({KEYS[key]})")
            self.send_response(200)
        except Exception as e:
            print(f"  error: {e}")
            self.send_response(500)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def log_message(self, *_):
        pass  # silence default access log

if __name__ == "__main__":
    print(f"HomeRemote ADB Proxy  —  port {PORT}")
    print(f"TV target : {TV}")
    print(f"ADB path  : {ADB}")
    print(f"Waiting for D-pad commands...\n")
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
