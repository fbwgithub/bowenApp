#!/bin/bash
SOCK=/tmp/tmuxsock
DIR=/root/bowenApp/backend
SERVER="node $DIR/server.js"
WD="$DIR/wd.sh"
PIDFILE="$DIR/.server_pid"

case "${1:-status}" in
  start)
    tmux -S $SOCK new-session -d -s backend "$SERVER"
    # Start watchdog in background
    nohup bash $WD > /dev/null 2>&1 &
    echo $! > $PIDFILE
    echo "✅ 启动 http://0.0.0.0:5000"
    ;;
  stop)
    kill $(cat $PIDFILE 2>/dev/null) 2>/dev/null
    rm -f $PIDFILE
    tmux -S $SOCK kill-session -t backend 2>/dev/null
    echo "✅ 已停止"
    ;;
  restart)
    kill $(cat $PIDFILE 2>/dev/null) 2>/dev/null
    rm -f $PIDFILE
    tmux -S $SOCK kill-session -t backend 2>/dev/null
    sleep 1
    tmux -S $SOCK new-session -d -s backend "$SERVER"
    nohup bash $WD > /dev/null 2>&1 &
    echo $! > $PIDFILE
    echo "✅ 已重启"
    ;;
  status)
    if tmux -S $SOCK has-session -t backend 2>/dev/null; then
      # Try actual request
      if curl -s --connect-timeout 3 --max-time 4 http://127.0.0.1:5000/api/stats >/dev/null 2>&1; then
        echo "✅ 运行中"
      else
        echo "⚠️ Session存在但无响应"
      fi
    else
      echo "❌ 已停止"
    fi
    ;;
  log)
    tail -30 $DIR/server.log 2>/dev/null || echo "无日志"
    ;;
  *)
    echo "用法: bash manage.sh {start|stop|restart|status|log}"
    ;;
esac
