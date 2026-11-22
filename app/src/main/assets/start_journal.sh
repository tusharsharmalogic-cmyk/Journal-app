#!/data/data/com.termux/files/usr/bin/bash
# Journal server starter. APK ke andar bundled hai; app ise `bash -c` se chalati hai.
# $1 = j.py ka base64 (app apne assets se bhejti hai) -> script j.py khud bana deti hai.

APP_DIR="$HOME/journal_server"
LOG="$HOME/journal_server.log"
PIDFILE="$APP_DIR/server.pid"

mkdir -p "$APP_DIR" || exit 1

# 1) j.py (re)create -- delete kar do to bhi har start par wapas ban jayegi
if [ -n "$1" ]; then
  if echo "$1" | base64 -d > "$APP_DIR/j.py.new" 2>/dev/null && [ -s "$APP_DIR/j.py.new" ]; then
    mv -f "$APP_DIR/j.py.new" "$APP_DIR/j.py"
  else
    rm -f "$APP_DIR/j.py.new"
  fi
fi

# 2) Pehle se chal raha ho to kuch mat karo
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE" 2>/dev/null)" 2>/dev/null; then
  exit 0
fi

: > "$LOG"

if [ ! -f "$APP_DIR/j.py" ]; then
  echo "[setup] j.py ban nahi payi." >> "$LOG"
  exit 1
fi

if ! command -v python >/dev/null 2>&1; then
  echo "[setup] python nahi mila. Termux me chalao: pkg install python" >> "$LOG"
  exit 1
fi

# 3) Python modules missing ho to khud install kar do
if ! python -c "import flask, flask_cors" >/dev/null 2>&1; then
  echo "[setup] flask, flask-cors install ho rahe hain..." >> "$LOG"
  pip install flask flask-cors >> "$LOG" 2>&1
fi

cd "$APP_DIR" || exit 1
termux-wake-lock 2>/dev/null
nohup python j.py >> "$LOG" 2>&1 &
PID=$!
echo "$PID" > "$PIDFILE"

(
  while kill -0 "$PID" 2>/dev/null; do sleep 5; done
  rm -f "$PIDFILE"
  termux-wake-unlock 2>/dev/null
) >/dev/null 2>&1 &
