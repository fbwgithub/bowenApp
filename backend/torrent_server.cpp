// torrent_server.cpp - Native C++ torrent engine
// Compile: g++ -std=c++17 -O2 torrent_server.cpp -o torrent_server -ltorrent-rasterbar -lpthread

#include <libtorrent/session.hpp>
#include <libtorrent/settings_pack.hpp>
#include <libtorrent/add_torrent_params.hpp>
#include <libtorrent/torrent_handle.hpp>
#include <libtorrent/torrent_info.hpp>
#include <libtorrent/alert_types.hpp>
#include <libtorrent/magnet_uri.hpp>
#include <libtorrent/error_code.hpp>
#include <libtorrent/announce_entry.hpp>
#include <libtorrent/hex.hpp>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <signal.h>
#include <cstring>
#include <string>
#include <map>
#include <sstream>
#include <iostream>
#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <sys/stat.h>

namespace lt = libtorrent;
using namespace std;

const int PORT = 18001;
const string DL_DIR = "downloads";

lt::session* g_ses = nullptr;
map<string, lt::torrent_handle> g_handles;
map<string, string> g_magnets;
mutex g_mtx;
atomic<bool> g_running{true};

string js(const string& s) {
    string r;
    r.reserve(s.size() + 8);
    for (char c : s) {
        if (c == '"' || c == '\\') r += '\\', r += c;
        else if (c == '\n') r += "\\n";
        else if (c == '\r') r += "\\r";
        else if (c == '\t') r += "\\t";
        else if ((unsigned char)c >= 0x20) r += c;
    }
    return r;
}

string fs(long long sz) {
    if (sz < 0) return "0 B";
    const char* u[] = {"B","KB","MB","GB","TB"};
    double s = sz; int i = 0;
    while (s >= 1024.0 && i < 4) s /= 1024.0, i++;
    char b[32]; snprintf(b, sizeof(b), "%.1f %s", s, u[i]);
    return b;
}

string st_str(lt::torrent_status::state_t st) {
    switch (st) {
        case lt::torrent_status::checking_files: return "checking_files";
        case lt::torrent_status::downloading_metadata: return "downloading_metadata";
        case lt::torrent_status::downloading: return "downloading";
        case lt::torrent_status::finished: return "finished";
        case lt::torrent_status::seeding: return "seeding";
        case lt::torrent_status::checking_resume_data: return "checking_resume_data";
        default: return "unknown";
    }
}

string build_status(const string& ih, const lt::torrent_handle& h) {
    ostringstream out;
    lt::torrent_status st = h.status();
    
    out << "{";
    out << "\"info_hash\":\"" << js(ih) << "\",";
    out << "\"magnet_uri\":\"" << js(g_magnets[ih]) << "\",";
    out << "\"name\":\"" << js(st.name) << "\",";
    out << "\"state\":\"" << st_str(st.state) << "\",";
    out << "\"progress\":" << (st.progress * 100.0) << ",";
    out << "\"download_rate\":" << st.download_rate << ",";
    out << "\"upload_rate\":" << st.upload_rate << ",";
    out << "\"num_peers\":" << st.num_peers << ",";
    out << "\"total_download\":" << st.total_download << ",";
    out << "\"total_size\":" << st.total << ",";
    out << "\"total_size_str\":\"" << fs(st.total) << "\",";
    out << "\"download_rate_str\":\"" << fs(st.download_rate) << "/s\",";
    out << "\"upload_rate_str\":\"" << fs(st.upload_rate) << "/s\",";

    out << "\"files\":[";
    if (st.has_metadata) {
        auto tf = h.torrent_file();
        auto& fl = tf->files();
        int nf = fl.num_files();
        vector<int64_t> fp(nf, 0);
        h.file_progress(fp, lt::torrent_handle::piece_granularity);
        for (int i = 0; i < nf; i++) {
            if (i > 0) out << ",";
            long long fsize = fl.file_size(i);
            long long fprog = (i < (int)fp.size()) ? fp[i] : 0;
            double pct = (fsize > 0) ? (double)fprog / fsize * 100.0 : 0.0;
            out << "{\"index\":" << i;
            out << ",\"path\":\"" << js(fl.file_path(i)) << "\"";
            out << ",\"size\":" << fsize;
            out << ",\"size_str\":\"" << fs(fsize) << "\"";
            out << ",\"progress\":" << pct << "}";
        }
    }
    out << "]}";
    return out.str();
}

void send_json(int fd, const string& body, int code = 200) {
    string resp = "HTTP/1.1 " + to_string(code) + " " +
                  (code == 200 ? "OK" : code == 404 ? "Not Found" : "Error") + "\r\n"
                  "Content-Type: application/json; charset=utf-8\r\n"
                  "Content-Length: " + to_string(body.size()) + "\r\n"
                  "Access-Control-Allow-Origin: *\r\n"
                  "Connection: close\r\n"
                  "\r\n" + body;
    auto r = write(fd, resp.data(), resp.size());
    (void)r; close(fd);
}

string json_str(const string& body, const string& key) {
    size_t p = body.find("\"" + key + "\"");
    if (p == string::npos) return "";
    p = body.find('"', p + key.size() + 2);
    if (p == string::npos) return "";
    size_t e = p + 1;
    string r;
    while (e < body.size() && body[e] != '"') {
        if (body[e] == '\\') { e++; if (e >= body.size()) break; }
        r += body[e++];
    }
    return r;
}

bool json_bool(const string& body, const string& key, bool def = false) {
    size_t p = body.find("\"" + key + "\"");
    if (p == string::npos) return def;
    p = body.find(':', p);
    if (p == string::npos) return def;
    p++;
    while (p < body.size() && body[p] == ' ') p++;
    return (p + 4 <= body.size() && body.substr(p, 4) == "true");
}

void handle_request(int fd) {
    char buf[8192];
    int n = read(fd, buf, sizeof(buf) - 1);
    if (n <= 0) { close(fd); return; }
    buf[n] = 0;
    
    string req(buf);
    size_t nl1 = req.find("\r\n");
    if (nl1 == string::npos) { close(fd); return; }
    
    istringstream rl(req.substr(0, nl1));
    string method, path, version;
    rl >> method >> path >> version;
    
    size_t qpos = path.find('?');
    if (qpos != string::npos) path = path.substr(0, qpos);
    
    size_t hdr_end = req.find("\r\n\r\n");
    string body = (hdr_end != string::npos) ? req.substr(hdr_end + 4) : "";
    
    // GET /
    if (method == "GET" && path == "/") {
        lock_guard<mutex> lock(g_mtx);
        ostringstream out; out << "[";
        bool first = true;
        for (auto& [ih, h] : g_handles) {
            if (!first) out << ",";
            first = false; out << build_status(ih, h);
        }
        out << "]";
        send_json(fd, out.str());
        return;
    }
    
    // GET /<info_hash>
    if (method == "GET" && path.size() == 41 && path[0] == '/') {
        string ih = path.substr(1);
        lock_guard<mutex> lock(g_mtx);
        auto it = g_handles.find(ih);
        if (it == g_handles.end()) { send_json(fd, "{\"error\":\"not found\"}", 404); return; }
        send_json(fd, build_status(ih, it->second));
        return;
    }
    
    // POST /parse - parse magnet using existing session
    if (method == "POST" && path == "/parse") {
        string magnet = json_str(body, "magnet");
        if (magnet.empty()) { send_json(fd, "{\"error\":\"no magnet\"}", 400); return; }
        
        size_t xt = magnet.find("xt=urn:btih:");
        if (xt == string::npos) { send_json(fd, "{\"error\":\"invalid magnet\"}", 400); return; }
        string eih = magnet.substr(xt + 12, 40);
        for (auto& c : eih) c = tolower(c);
        
        // Check if we already have this torrent
        {
            lock_guard<mutex> lock(g_mtx);
            auto it = g_handles.find(eih);
            if (it != g_handles.end() && it->second.status().has_metadata) {
                send_json(fd, build_status(eih, it->second));
                return;
            }
        }
        
        // Try HTTP cache first
        bool cache_ok = false;
        string cache_urls[] = {
            "https://itorrents.org/torrent/" + eih + ".torrent",
            "https://btcache.me/torrent/" + eih,
            "http://itorrents.org/torrent/" + eih + ".torrent",
        };
        for (auto& curl : cache_urls) {
            lt::add_torrent_params cp;
            cp.save_path = DL_DIR;
            cp.url = curl;
            lt::error_code ec2;
            lt::torrent_handle ch = g_ses->add_torrent(cp, ec2);
            if (ec2) continue;
            for (int w = 0; w < 6; w++) {
                if (ch.status().has_metadata) {
                    cache_ok = true;
                    lock_guard<mutex> lock(g_mtx);
                    g_handles[eih] = ch;
                    g_magnets[eih] = magnet;
                    send_json(fd, build_status(eih, ch));
                    return;
                }
                this_thread::sleep_for(chrono::milliseconds(500));
            }
            g_ses->remove_torrent(ch);
        }
        if (cache_ok) return;
        
        // Cache failed, add via DHT/trackers
        lt::add_torrent_params p;
        p.save_path = DL_DIR;
        lt::error_code ec;
        lt::parse_magnet_uri(magnet, p, ec);
        if (ec) p.url = magnet;
        
        lt::torrent_handle h = g_ses->add_torrent(p, ec);
        if (ec) { send_json(fd, "{\"error\":\"" + js(ec.message()) + "\"}", 500); return; }
        
        {
            lock_guard<mutex> lock(g_mtx);
            g_handles[eih] = h;
            g_magnets[eih] = magnet;
        }
        
        h.add_tracker(lt::announce_entry("http://tracker.opentrackr.org:1337/announce"));
        h.add_tracker(lt::announce_entry("http://tracker.openbittorrent.com:80/announce"));
        h.add_tracker(lt::announce_entry("http://tracker.coppersurfer.tk:6969/announce"));
        h.force_reannounce();
        
        // Wait for metadata
        bool got_meta = false;
        for (int i = 0; i < 45; i++) {
            if (h.status().has_metadata) { got_meta = true; break; }
            this_thread::sleep_for(chrono::seconds(1));
        }
        
        if (got_meta) {
            send_json(fd, build_status(eih, h));
        } else {
            // Remove and return error
            {
                lock_guard<mutex> lock(g_mtx);
                g_handles.erase(eih);
                g_magnets.erase(eih);
            }
            g_ses->remove_torrent(h);
            send_json(fd, "{\"success\":false,\"error\":\"parse timeout\"}", 408);
        }
        return;
    }

    // POST /
    if (method == "POST" && path == "/") {
        string magnet = json_str(body, "magnet");
        string ih = json_str(body, "info_hash");
        string cmd = json_str(body, "cmd");
        bool del_files = json_bool(body, "delete_files", false);
        
        // Convert magnet to info_hash
        string eih;
        if (!magnet.empty()) {
            size_t xt = magnet.find("xt=urn:btih:");
            if (xt != string::npos) {
                eih = magnet.substr(xt + 12, 40);
                for (auto& c : eih) c = tolower(c);
            }
        }
        if (!ih.empty()) eih = ih;
        
        // ADD
        if (cmd == "add" || (cmd.empty() && !magnet.empty())) {
            if (eih.empty()) { send_json(fd, "{\"error\":\"invalid magnet\"}", 400); return; }
            
            lock_guard<mutex> lock(g_mtx);
            if (g_handles.find(eih) != g_handles.end()) {
                send_json(fd, "{\"error\":\"exists\",\"info_hash\":\"" + js(eih) + "\"}");
                return;
            }
            
            lt::add_torrent_params p;
            p.save_path = DL_DIR;
            lt::error_code ec;
            lt::parse_magnet_uri(magnet, p, ec);
            if (ec) p.url = magnet;
            
            lt::torrent_handle h = g_ses->add_torrent(p, ec);
            if (ec) { send_json(fd, "{\"error\":\"" + js(ec.message()) + "\"}", 500); return; }
            
            g_handles[eih] = h;
            g_magnets[eih] = magnet;
            
            // Add HTTP trackers immediately
            h.add_tracker(lt::announce_entry("http://tracker.openbittorrent.com:80/announce"));
            h.add_tracker(lt::announce_entry("http://tracker.coppersurfer.tk:6969/announce"));
            h.force_reannounce();
            if (h.status().has_metadata) {
                lt::announce_entry ae;
                ae.url = "http://tracker.opentrackr.org:1337/announce"; ae.tier = 1; h.add_tracker(ae);
                ae.url = "http://tracker.openbittorrent.com:80/announce"; h.add_tracker(ae);
                ae.url = "http://tracker.coppersurfer.tk:6969/announce"; h.add_tracker(ae);
                h.force_reannounce();
            }
            for (int i = 0; i < 20; i++) {
                if (h.status().has_metadata) break;
                this_thread::sleep_for(chrono::milliseconds(500));
            }
            
            send_json(fd, "{\"success\":true,\"info_hash\":\"" + js(eih) + "\"}");
            return;
        }
        
        // REMOVE
        if (cmd == "remove") {
            lock_guard<mutex> lock(g_mtx);
            auto it = g_handles.find(eih);
            if (it != g_handles.end()) {
                g_ses->remove_torrent(it->second, del_files ? lt::session::delete_files : lt::remove_flags_t{});
                g_handles.erase(it);
                g_magnets.erase(eih);
            }
            send_json(fd, "{\"success\":true}");
            return;
        }
        
        // PAUSE
        if (cmd == "pause") {
            lock_guard<mutex> lock(g_mtx);
            auto it = g_handles.find(eih);
            if (it != g_handles.end()) it->second.pause();
            send_json(fd, "{\"success\":true}");
            return;
        }
        
        // RESUME
        if (cmd == "resume") {
            lock_guard<mutex> lock(g_mtx);
            auto it = g_handles.find(eih);
            if (it != g_handles.end()) it->second.resume();
            send_json(fd, "{\"success\":true}");
            return;
        }
        
        send_json(fd, "{\"error\":\"unknown cmd\"}", 400);
        return;
    }
    
    send_json(fd, "{\"error\":\"not found\"}", 404);
}

void alert_loop() {
    while (g_running) {
        if (!g_ses) { this_thread::sleep_for(chrono::milliseconds(100)); continue; }
        vector<lt::alert*> alerts;
        g_ses->pop_alerts(&alerts);
        for (auto* a : alerts) {
            if (auto* at = lt::alert_cast<lt::add_torrent_alert>(a)) {
                if (at->error) cerr << "[ALERT] add_torrent error: " << at->error.message() << endl;
            }
            if (lt::alert_cast<lt::torrent_finished_alert>(a)) {
                cerr << "[ALERT] torrent finished" << endl;
            }
        }
        this_thread::sleep_for(chrono::milliseconds(200));
    }
}

// Parse mode: ./torrent_server --parse <magnet> [save_path]
void do_parse(const string& magnet, const string& save_path) {
    lt::session ses;
    lt::settings_pack sp;
    sp.set_str(lt::settings_pack::listen_interfaces, "0.0.0.0:0");
    sp.set_bool(lt::settings_pack::enable_dht, true);
    sp.set_bool(lt::settings_pack::enable_lsd, true);
    ses.apply_settings(sp);

    lt::add_torrent_params p;
    p.save_path = save_path;
    lt::error_code ec;
    lt::parse_magnet_uri(magnet, p, ec);
    if (ec) {
        cout << "{\"success\":false,\"error\":\"" << js(ec.message()) << "\"}";
        return;
    }

    lt::torrent_handle h = ses.add_torrent(p, ec);
    if (ec) {
        cout << "{\"success\":false,\"error\":\"" << js(ec.message()) << "\"}";
        return;
    }

    for (int i = 0; i < 60; i++) {
        auto st = h.status();
        if (st.has_metadata) {
            auto tf = h.torrent_file();
            auto& fl = tf->files();
            int nf = fl.num_files();
            size_t xt = magnet.find("xt=urn:btih:");
            string ih = (xt != string::npos) ? magnet.substr(xt + 12, 40) : "";
            ostringstream out;
            out << "{\"success\":true,\"name\":\"" << js(tf->name()) << "\",";
            out << "\"info_hash\":\"" << js(ih) << "\",";
            out << "\"total_size\":" << tf->total_size() << ",";
            out << "\"total_size_str\":\"" << fs(tf->total_size()) << "\",";
            out << "\"files\":[";
            for (int j = 0; j < nf; j++) {
                if (j > 0) out << ",";
                out << "{\"index\":" << j;
                out << ",\"path\":\"" << js(fl.file_path(j)) << "\"";
                out << ",\"size\":" << fl.file_size(j);
                out << ",\"size_str\":\"" << fs(fl.file_size(j)) << "\"}";
            }
            out << "]}";
            cout << out.str() << endl;
            ses.remove_torrent(h);
            return;
        }
        this_thread::sleep_for(chrono::seconds(1));
    }
    cout << "{\"success\":false,\"error\":\"parse timeout\"}" << endl;
    ses.remove_torrent(h);
}

int main(int argc, char* argv[]) {
    signal(SIGPIPE, SIG_IGN);
    
    if (argc >= 3 && string(argv[1]) == "--parse") {
        do_parse(argv[2], (argc > 3) ? argv[3] : ".");
        return 0;
    }
    
    mkdir(DL_DIR.c_str(), 0755);
    
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) { cerr << "[ERR] socket" << endl; return 1; }
    int opt = 1;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(PORT);
    
    if (bind(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        cerr << "[ERR] bind port " << PORT << ": " << strerror(errno) << endl; return 1;
    }
    listen(sock, 10);
    cerr << "[OK] Listening on port " << PORT << endl;
    
    try {
        g_ses = new lt::session();
        lt::settings_pack sp;
        sp.set_str(lt::settings_pack::listen_interfaces, "0.0.0.0:0");
        sp.set_bool(lt::settings_pack::enable_dht, true);
        sp.set_bool(lt::settings_pack::enable_lsd, true);
        sp.set_bool(lt::settings_pack::enable_upnp, false);
        sp.set_bool(lt::settings_pack::enable_natpmp, false);
        sp.set_int(lt::settings_pack::dht_announce_interval, 30);
        sp.set_int(lt::settings_pack::alert_mask, lt::alert::status_notification | lt::alert::error_notification);
        g_ses->apply_settings(sp);
        g_ses->add_dht_node(make_pair("router.bittorrent.com", 6881));
        g_ses->add_dht_node(make_pair("router.utorrent.com", 6881));
        g_ses->add_dht_node(make_pair("dht.transmissionbt.com", 6881));
    } catch (const std::exception& e) {
        cerr << "[WARN] libtorrent init: " << e.what() << " (continuing without DHT)" << endl;
    } catch (...) {
        cerr << "[WARN] libtorrent init unknown error (continuing)" << endl;
    }
    
    thread al(alert_loop);
    
    while (g_running) {
        struct sockaddr_in client;
        socklen_t clen = sizeof(client);
        int cfd = accept(sock, (struct sockaddr*)&client, &clen);
        if (cfd < 0) { if (errno == EINTR) continue; break; }
        thread(handle_request, cfd).detach();
    }
    
    g_running = false;
    close(sock);
    al.join();
    if (g_ses) delete g_ses;
    return 0;
}
