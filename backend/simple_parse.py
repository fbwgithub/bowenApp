"""磁力解析器 - HTTP缓存+DHT双重保障，结果本地缓存"""
import sys, json, os, time, urllib.request

CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'torrent_cache')
os.makedirs(CACHE_DIR, exist_ok=True)

CACHE_URLS = [
    "https://itorrents.org/torrent/{hash_lower}.torrent",
    "https://itorrents.org/torrent/{hash}.torrent",
]

def extract_info_hash(magnet):
    import re
    m = re.search(r"xt=urn:btih:([a-fA-F0-9]+)", magnet)
    return m.group(1).lower() if m else None

def fetch_http(info_hash):
    """从 HTTP 缓存获取 .torrent 文件"""
    for url_tpl in CACHE_URLS:
        url = url_tpl.format(hash=info_hash.upper(), hash_lower=info_hash)
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'curl/8.5.0'})
            data = urllib.request.urlopen(req, timeout=10).read()
            return data
        except: pass
    return None

def resolve_via_dht(magnet, info_hash, download_dir):
    """通过 DHT 网络获取元数据"""
    import libtorrent as lt
    sess = lt.session()
    sess.apply_settings({
        'listen_interfaces': '0.0.0.0:0',
        'enable_dht': True, 'enable_lsd': False, 'enable_upnp': False, 'enable_natpmp': False,
    })
    sess.add_dht_router("router.bittorrent.com", 6881)
    sess.add_dht_router("router.utorrent.com", 6881)
    sess.add_dht_router("dht.transmissionbt.com", 6881)
    handle = lt.add_magnet_uri(sess, magnet, {'save_path': download_dir,
        'storage_mode': lt.storage_mode_t.storage_mode_sparse})
    waited = 0
    while not handle.has_metadata() and waited < 35:
        time.sleep(0.5); waited += 0.5
        if int(waited) % 10 == 0: sys.stderr.write(f"  DHT waiting {int(waited)}s\n")
    if not handle.has_metadata():
        sess.remove_torrent(handle)
        return None
    info = handle.torrent_file()
    # 尝试保存 .torrent 文件到缓存
    try:
        td = lt.bencode(lt.create_torrent(info))
        cp = os.path.join(CACHE_DIR, info_hash + '.torrent')
        with open(cp, 'wb') as f: f.write(td)
    except: pass
    files = []; total = 0
    for i in range(info.num_files()):
        fe = info.file_at(i); sz = fe.size; total += sz
        files.append({'index':i, 'path':fe.path, 'size':sz, 'size_str':_fmt(sz), 'offset':fe.offset})
    result = {"success":True, "name":info.name(), "total_size_str":_fmt(total),
              "info_hash":info_hash, "files":files}
    sess.remove_torrent(handle)
    return result

def parse_torrent_data(data):
    """解析 .torrent 文件内容"""
    import tempfile, libtorrent as lt
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.torrent')
    tmp.write(data); tmp.close()
    info = lt.torrent_info(tmp.name)
    os.unlink(tmp.name)
    files = []; total = 0
    for i in range(info.num_files()):
        fe = info.file_at(i); sz = fe.size; total += sz
        files.append({'index':i, 'path':fe.path, 'size':sz, 'size_str':_fmt(sz), 'offset':fe.offset})
    return {"success":True, "name":info.name(), "total_size_str":_fmt(total),
            "info_hash":str(info.info_hash()), "files":files}

def _fmt(sz):
    for u in ['B','KB','MB','GB','TB']:
        if sz < 1024: return f"{sz:.2f} {u}"
        sz /= 1024
    return f"{sz:.2f} PB"

if __name__ == '__main__':
    arg = sys.argv[1] if len(sys.argv) > 1 else ''
    download_dir = sys.argv[2] if len(sys.argv) > 2 else '/tmp'

    if arg.startswith('file://'):
        with open(arg[7:], 'rb') as f:
            data = f.read()
        result = parse_torrent_data(data)
        print(json.dumps(result))
        sys.exit(0)

    ih = extract_info_hash(arg) if arg.startswith('magnet:') else None
    if not ih:
        print(json.dumps({"success":False,"error":"无法提取 info_hash"}))
        sys.exit(1)

    # 1. 查缓存
    cache_path = os.path.join(CACHE_DIR, ih + '.torrent')
    if os.path.exists(cache_path):
        with open(cache_path, 'rb') as f:
            result = parse_torrent_data(f.read())
        print(json.dumps(result))
        sys.exit(0)

    # 2. HTTP 缓存
    sys.stderr.write("Trying HTTP cache...\n")
    data = fetch_http(ih)
    if data:
        # 保存缓存
        try:
            with open(cache_path, 'wb') as f: f.write(data)
        except: pass
        result = parse_torrent_data(data)
        print(json.dumps(result))
        sys.exit(0)

    # 3. DHT 解析
    sys.stderr.write("HTTP failed, trying DHT...\n")
    result = resolve_via_dht(arg, ih, download_dir)
    if result:
        print(json.dumps(result))
        sys.exit(0)

    print(json.dumps({"success":False,"error":"无法解析磁力链接"}))
    sys.exit(1)
