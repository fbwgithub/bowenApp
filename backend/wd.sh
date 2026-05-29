#!/bin/bash
SOCK=/tmp/tmuxsock
DIR=/root/bowenApp/backend
SERVER="node $DIR/server.js"

while true; do
  if ! tmux -S $SOCK has-session -t backend 2>/dev/null; then
    echo "[$(date '+%H:%M:%S')] ⚠️ Session dead, restarting..." >> $DIR/server.log
    tmux -S $SOCK new-session -d -s backend "$SERVER"
  fi
  sleep 10
done
