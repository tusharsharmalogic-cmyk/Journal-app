#!/data/data/com.termux/files/usr/bin/bash
# Journal server on-demand starter (Android app isse call karega)
# Agar server pehle se chal raha hai to port busy hoga aur ye script chupchap exit ho jayegi.

cd /sdcard/Journal/main_script || exit 1

# Termux ko background mein zinda rakho jab tak server chal raha hai
termux-wake-lock 2>/dev/null

nohup python j.py > /data/data/com.termux/files/home/journal_server.log 2>&1 &

# Server band hone ke baad wake-lock chhod do
(
  while pgrep -f "python j.py" > /dev/null; do sleep 5; done
  termux-wake-unlock 2>/dev/null
) > /dev/null 2>&1 &