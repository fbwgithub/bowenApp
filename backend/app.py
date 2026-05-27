import os, time, mimetypes, tempfile, urllib.request
from flask import Flask, request, jsonify, render_template, Response, send_file, stream_with_context
from flask_cors import CORS
from download_manager import DownloadManager

app = Flask(__name__)
CORS(app)

download_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'downloads')
dm = DownloadManager(download_dir=download_dir)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/torrents', methods=['GET'])
def list_torrents():
    return jsonify(list(dm.get_torrents().values()))

@app.route('/api/torrents', methods=['POST'])
def add_torrent():
    data = request.get_json()
    if not data or 'magnet' not in data:
        return jsonify({'error': '缺少磁力链接'}), 400
    magnet = data['magnet'].strip()
    if not magnet.startswith('magnet:'):
        return jsonify({'error': '无效的磁力链接'}), 400
    try:
        info = dm.add_magnet(magnet)
        return jsonify({'success': True, 'torrent': info}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/torrents/<info_hash>', methods=['GET'])
def get_torrent(info_hash):
    t = dm.get_torrent(info_hash)
    if t is None:
        return jsonify({'error': '未找到'}), 404
    return jsonify(t)

@app.route('/api/torrents/<info_hash>', methods=['DELETE'])
def remove_torrent(info_hash):
    d = request.args.get('delete_files', 'false').lower() == 'true'
    if not dm.remove_torrent(info_hash, d):
        return jsonify({'error': '未找到'}), 404
    return jsonify({'success': True})

@app.route('/api/torrents/<info_hash>/pause', methods=['POST'])
def pause_torrent(info_hash):
    with dm.lock:
        if info_hash not in dm.torrents:
            return jsonify({'error': '未找到'}), 404
        dm.torrents[info_hash]['handle'].pause()
    return jsonify({'success': True})

@app.route('/api/torrents/<info_hash>/resume', methods=['POST'])
def resume_torrent(info_hash):
    with dm.lock:
        if info_hash not in dm.torrents:
            return jsonify({'error': '未找到'}), 404
        dm.torrents[info_hash]['handle'].resume()
    return jsonify({'success': True})

@app.route('/api/magnet/parse', methods=['POST'])
def parse_magnet():
    data = request.get_json()
    if not data or 'magnet' not in data:
        return jsonify({'success': False, 'error': '缺少磁力链接'}), 400
    magnet = data['magnet'].strip()
    if not magnet.startswith('magnet:'):
        return jsonify({'success': False, 'error': '无效的磁力链接'}), 400
    try:
        result = dm.parse_magnet(magnet)
        return jsonify({
            'success': True,
            'name': result['name'],
            'total_size_str': result['total_size_str'],
            'info_hash': result['info_hash'],
            'files': result['files'],
        })
    except TimeoutError as e:
        return jsonify({'success': False, 'error': str(e)}), 408
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/magnet/download', methods=['POST'])
def download_selected():
    data = request.get_json()
    if not data or 'magnet' not in data:
        return jsonify({'success': False, 'error': '缺少磁力链接'}), 400
    magnet = data['magnet'].strip()
    selected = data.get('selectedFiles', [])
    if not selected:
        return jsonify({'success': False, 'error': '请选择要下载的文件'}), 400
    try:
        info = dm.add_magnet_with_selection(magnet, selected)
        return jsonify({'success': True, 'torrent': info}), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

# === NEW: Upload .torrent file ===
@app.route('/api/torrent/upload', methods=['POST'])
def upload_torrent():
    if 'file' not in request.files:
        return jsonify({'error': '请上传 .torrent 文件'}), 400
    f = request.files['file']
    if f.filename == '':
        return jsonify({'error': '请选择文件'}), 400
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.torrent')
    f.save(tmp.name)
    try:
        info = dm._parse_torrent_file(tmp.name)  # Returns info without adding
        # Now add it properly
        info2 = dm.add_torrent_file(tmp.name)
        s = dm.get_torrent(info2['info_hash'])
        return jsonify({'success': True, 'torrent': s}), 201
    except Exception as e:
        os.unlink(tmp.name)
        return jsonify({'error': str(e)}), 500

# === NEW: Add torrent from URL ===
@app.route('/api/torrent/url', methods=['POST'])
def add_torrent_url():
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({'error': '缺少种子文件 URL'}), 400
    url = data['url'].strip()
    try:
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36'
        })
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.torrent')
        tmp.write(data)
        tmp.close()
        info = dm.add_torrent_file(tmp.name)
        s = dm.get_torrent(info['info_hash'])
        return jsonify({'success': True, 'torrent': s}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/magnet/play', methods=['POST'])
def play_magnet():
    """Add magnet with single file selection and return stream URL"""
    data = request.get_json()
    if not data or 'magnet' not in data:
        return jsonify({'success': False, 'error': '缺少磁力链接'}), 400
    magnet = data['magnet'].strip()
    file_index = data.get('file_index', 0)
    try:
        info = dm.add_magnet_with_selection(magnet, [file_index])
        info_hash = info['info_hash']
        # Wait for serialization to be available
        import time as _t2
        serialized = None
        for _ in range(20):
            serialized = dm.get_torrent(info_hash)
            if serialized: break
            _t2.sleep(0.5)
        # Wait for file to appear on disk (sequential download)
        import time as _t
        file_path, _ = dm.get_file_path(info_hash, file_index)
        waited = 0
        while file_path and not os.path.exists(file_path) and waited < 10:
            _t.sleep(0.5)
            waited += 0.5
        stream_url = f'/api/stream/{info_hash}/{file_index}'
        return jsonify({
            'success': True,
            'stream_url': stream_url,
            'info_hash': info_hash,
            'file_index': file_index,
            'name': serialized['files'][file_index]['path'] if serialized and file_index < len(serialized['files']) else '',
        }), 201
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/stream/<info_hash>/<int:file_index>')
def stream_file(info_hash, file_index):
    file_path, file_size = dm.get_file_path(info_hash, file_index)
    if file_path is None:
        return jsonify({'error': '文件未找到'}), 404
    actual_size = os.path.getsize(file_path) if os.path.exists(file_path) else 0
    if actual_size == 0:
        t = dm.get_torrent(info_hash)
        if t and file_index < len(t['files']):
            actual_size = t['files'][file_index]['size']
    
    mime_type, _ = mimetypes.guess_type(file_path)
    if mime_type is None:
        mime_type = 'application/octet-stream'
    
    range_header = request.headers.get('Range', None)
    
    if range_header:
        byte1, byte2 = 0, None
        parts = range_header.replace('bytes=', '').split('-')
        byte1 = int(parts[0])
        if len(parts) > 1 and parts[1]:
            byte2 = int(parts[1])
        if byte2 is None:
            byte2 = actual_size - 1
        length = byte2 - byte1 + 1
        
        def gen_range():
            with open(file_path, 'rb') as f:
                f.seek(byte1)
                remaining = length
                while remaining > 0:
                    chunk = f.read(min(65536, remaining))
                    if not chunk: break
                    remaining -= len(chunk)
                    yield chunk
        
        resp = Response(stream_with_context(gen_range()), status=206, mimetype=mime_type)
        resp.headers['Content-Range'] = f'bytes {byte1}-{byte2}/{actual_size}'
        resp.headers['Accept-Ranges'] = 'bytes'
        resp.headers['Content-Length'] = str(length)
        return resp
    else:
        def gen_full():
            with open(file_path, 'rb') as f:
                while True:
                    data = f.read(65536)
                    if not data: break
                    yield data
        resp = Response(stream_with_context(gen_full()), status=200, mimetype=mime_type)
        resp.headers['Accept-Ranges'] = 'bytes'
        resp.headers['Content-Length'] = str(actual_size)
        return resp

@app.route('/api/download/<info_hash>/<int:file_index>')
def download_file(info_hash, file_index):
    file_path, _ = dm.get_file_path(info_hash, file_index)
    if file_path is None or not os.path.exists(file_path):
        return jsonify({'error': '文件未就绪'}), 404
    t = dm.get_torrent(info_hash)
    filename = t['files'][file_index]['path'].split('/')[-1] if t else 'download'
    return send_file(file_path, as_attachment=True, download_name=filename)

@app.route('/api/stats')
def get_stats():
    torrents = dm.get_torrents()
    return jsonify({
        'total_torrents': len(torrents),
        'active_downloads': sum(1 for t in torrents.values() if t['state'] == 'downloading'),
        'total_download_rate': dm._format_speed(sum(t['download_rate'] for t in torrents.values())),
        'total_upload_rate': dm._format_speed(sum(t['upload_rate'] for t in torrents.values())),
        'download_rate_bytes': sum(t['download_rate'] for t in torrents.values()),
        'upload_rate_bytes': sum(t['upload_rate'] for t in torrents.values()),
    })

if __name__ == '__main__':
    os.makedirs(download_dir, exist_ok=True)
    os.makedirs(os.path.join(os.path.dirname(__file__), 'templates'), exist_ok=True)
    print(f"Backend running on http://0.0.0.0:5000")
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
