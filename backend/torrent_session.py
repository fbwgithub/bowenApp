"""Torrent session - standalone HTTP server on port 5001"""
import sys, json, os, threading, time, re, http.server, urllib.parse
import libtorrent as lt

BASE = os.path.dirname(os.path.abspath(__file__))
DL_DIR = os.path.join(BASE, 'downloads')
CACHE_DIR = os.path.join(BASE, 'torrent_cache')
PORT = 5001
os.makedirs(DL_DIR, exist_ok=True)

ses = None
handles = {}  # info_hash -> handle
lock = threading.Lock()

def log(msg):
    sys.stderr.write(f"[{time.strftime('%H:%M:%S')}] {msg}\n")
    sys.stderr.flush()

def fmt(sz):
    for u in ['B','KB','MB','GB','TB']:
        if sz < 1024: return f"{sz:.1f} {u}"
        sz /= 1024
    return f"{sz:.1f} PB"

def init():
    global ses
    ses = lt.session()
magnet_map = {}  # info_hash -> original magnet URI
    ses.apply_settings({
        'listen_interfaces': '0.0.0.0:0',
        'enable_dht': True,
        'enable_lsd': True,
        'enable_upnp': False,
        'enable_natpmp': False,
        'dht_announce_interval': 30,
        'alert_mask': 0,
    })
    for node in ["router.bittorrent.com","router.utorrent.com","dht.transmissionbt.com"]:
        ses.add_dht_node((node, 6881))
    log("Init OK")

def json_resp(handler, data, code=200):
    body = json.dumps(data, ensure_ascii=False).encode()
    handler.send_response(code)
    handler.send_header('Content-Type', 'application/json; charset=utf-8')
    handler.send_header('Access-Control-Allow-Origin', '*')
    handler.send_header('Content-Length', len(body))
    handler.end_headers()
    handler.wfile.write(body)

def build_status(ih, handle):
    try:
        st = handle.status()
        if not st.has_metadata:
            return {"info_hash":ih,"state":"downloading_metadata","progress":0}
        info = handle.torrent_file()
        files = []
        for i in range(info.num_files()):
            fe = info.file_at(i)
            try:
                fp = handle.file_progress(i)
                pct = round(fp/fe.size*100,1) if fe.size>0 else 0
            except:
                pct = 0
            files.append({"index":i,"path":fe.path,"size":fe.size,"size_str":fmt(fe.size),"progress":pct})
        return {
            "info_hash":ih,"name":info.name(),"state":str(st.state),
            "progress":round(st.progress*100,1),
            "download_rate":st.download_rate,"upload_rate":st.upload_rate,
            "num_peers":st.num_peers,"total_download":st.total_download,
            "total_size":st.total,"files":files,
        }
    except Exception as e:
        log(f"build_status error for {ih}: {e}")
        return {"info_hash":ih,"state":"error","progress":0}

def extract_hash(magnet):
    m = re.search(r"xt=urn:btih:([a-fA-F0-9]+)", magnet)
            "magnet_uri": magnet_map.get(ih, ""),
    return m.group(1).lower() if m else None

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        path = url.path

        # GET / - list all torrents
        if path == '/':
            with lock:
                result = []
                for ih, handle in handles.items():
                    result.append(build_status(ih, handle))
            json_resp(self, result)
            return

        # GET /<info_hash> - single torrent status
        if len(path.rstrip('/')) == 40:
            ih = path.strip('/')
            with lock:
                handle = handles.get(ih)
            if not handle:
                json_resp(self, {"error":"not found"}, 404)
                return
            json_resp(self, build_status(ih, handle))
            return

        json_resp(self, {"error":"not found"}, 404)

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = json.loads(self.rfile.read(length)) if length else {}
        magnet = body.get('magnet', '')
        cmd = body.get('cmd', '')

        if cmd == 'add' or cmd == '':
            ih = extract_hash(magnet)
            if not ih:
                json_resp(self, {"error":"invalid magnet"}, 400)
                return
            with lock:
                if ih in handles:
                    json_resp(self, {"error":"exists","info_hash":ih})
                    return

            selected = body.get('selected_files')
            params = lt.add_torrent_params()
            params.save_path = DL_DIR

            # Use cached torrent if available
            cache_path = os.path.join(CACHE_DIR, ih + '.torrent')
            if os.path.exists(cache_path):
                with open(cache_path,'rb') as f:
                    params.ti = lt.torrent_info(f.read())
                log(f"From cache: {ih}")
            else:
                params.url = magnet
                log(f"From magnet: {ih}")

            try:
                handle = ses.add_torrent(params)
            except Exception as e:
                json_resp(self, {"error":str(e)}, 500)
                return

            # Wait briefly for metadata
            for _ in range(10):
                if handle.status().has_metadata: break
                time.sleep(0.5)

            info = handle.torrent_file() if handle.status().has_metadata else None
            if info:
                # Add HTTP trackers
                for url in ['http://tracker.opentrackr.org:1337/announce',
                            'http://tracker.openbittorrent.com:80/announce']:
                    try: handle.add_tracker({'url':url,'tier':1})
                    except: pass
                # File priorities
                if selected is not None:
                    for i in range(info.num_files()):
                        handle.file_priority(i, 4 if i in selected else 0)
                handle.resume()
                log(f"Added: {info.name()}")

            with lock:
                handles[ih] = handle
                magnet_map[ih] = magnet
            json_resp(self, {"success":True,"info_hash":ih})
            return

        if cmd == 'remove':
            ih = body.get('info_hash','')
            del_files = body.get('delete_files', False)
            with lock:
                handle = handles.pop(ih, None)
            if handle:
                flags = lt.options_t.delete_files if del_files else 0
                ses.remove_torrent(handle, flags)
            json_resp(self, {"success":True})
            return

        if cmd == 'pause':
            ih = body.get('info_hash','')
            with lock:
                h = handles.get(ih)
            if h: h.pause()
            json_resp(self, {"success":True})
            return

        if cmd == 'resume':
            ih = body.get('info_hash','')
            with lock:
                h = handles.get(ih)
            if h: h.resume()
            json_resp(self, {"success":True})
            return

        json_resp(self, {"error":"unknown cmd"}, 400)

    def log_message(self, fmt, *args):
        pass  # suppress HTTP log

if __name__ == '__main__':
    init()
    server = http.server.HTTPServer(('127.0.0.1', PORT), Handler)
    log(f"Torrent session on port {PORT}")
    server.serve_forever()
