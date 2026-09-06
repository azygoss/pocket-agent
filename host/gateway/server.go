// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"sort"
	"strings"
)

// Server binds ONLY 127.0.0.1:24543. Token from 0600 file; Android reaches it
// only via SSH local-forward. Direct LAN/WAN exposure is refused by bind addr.
type Server struct {
	Token string
	Root  string // workspace root jail
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Host != "" && !strings.HasPrefix(r.Host, "127.0.0.1") && !strings.HasPrefix(r.Host, "localhost") {
		http.Error(w, "gateway is loopback-only", 403)
		return
	}
	got := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	if subtle.ConstantTimeCompare([]byte(got), []byte(s.Token)) != 1 {
		http.Error(w, "unauthorized", 401)
		return
	}
	// /ls/<rel>: jail içi dizin listesi (JSON, dizinler önce). Uygulamanın
	// workspace gezgini buradan beslenir; 1MB dosya sınırı burada yok ama
	// listing 4096 girdide kesilir.
	if strings.HasPrefix(r.URL.Path, "/ls/") || r.URL.Path == "/ls" {
		s.serveLs(w, r)
		return
	}
	// /diff?kind=staged|unstaged|untracked|working|last — workspace kökünde git.
	if strings.HasPrefix(r.URL.Path, "/diff") {
		s.serveDiff(w, r)
		return
	}
	// /preview?h=127.0.0.1&p=3000&path=/ — loopback dev-server önizlemesi (SSRF gate).
	if strings.HasPrefix(r.URL.Path, "/preview") {
		s.servePreview(w, r)
		return
	}
	// /chat?path=<rel> — jail içi agent transcript'i (JSONL) → sohbet blokları (P14).
	if strings.HasPrefix(r.URL.Path, "/chat") {
		s.serveChat(w, r)
		return
	}
	rel := strings.TrimPrefix(r.URL.Path, "/file/")
	if rel == r.URL.Path {
		http.Error(w, "bad path", 400)
		return
	}
	full, err := Jail(s.Root, rel)
	if err != nil {
		http.Error(w, "rejected", 400)
		return
	}
	if err := TextOnly(full); err != nil {
		http.Error(w, "binary listed, not rendered", 415)
		return
	}
	b, err := os.ReadFile(full)
	if err != nil {
		http.Error(w, "not found", 404)
		return
	}
	if len(b) > 1<<20 {
		b = b[:1<<20]
	}
	w.Write(b)
}

type lsEntry struct {
	Name  string `json:"name"`
	Dir   bool   `json:"dir"`
	Size  int64  `json:"size"`
	Mtime int64  `json:"mtime"`
}

func (s *Server) serveLs(w http.ResponseWriter, r *http.Request) {
	rel := strings.TrimPrefix(r.URL.Path, "/ls/")
	rel = strings.TrimPrefix(rel, "/ls")
	rel = strings.TrimPrefix(rel, "/")
	full, err := Jail(s.Root, rel)
	if err != nil {
		http.Error(w, "rejected", 400)
		return
	}
	items, err := os.ReadDir(full)
	if err != nil {
		http.Error(w, "not found", 404)
		return
	}
	out := make([]lsEntry, 0, len(items))
	for _, it := range items {
		if len(out) >= 4096 {
			break
		}
		info, err := it.Info()
		if err != nil {
			continue
		}
		out = append(out, lsEntry{Name: it.Name(), Dir: it.IsDir(), Size: info.Size(), Mtime: info.ModTime().Unix()})
	}
	// dizinler önce, sonra ada göre
	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Dir != out[j].Dir {
			return out[i].Dir
		}
		return strings.ToLower(out[i].Name) < strings.ToLower(out[j].Name)
	})
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(out)
}

func (s *Server) serveDiff(w http.ResponseWriter, r *http.Request) {
	kind := r.URL.Query().Get("kind")
	out, err := GitDiff(s.Root, kind)
	if err != nil {
		http.Error(w, "diff failed: "+err.Error(), 500)
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Write([]byte(out))
}

func (s *Server) servePreview(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	host := q.Get("h")
	port := 0
	fmt.Sscanf(q.Get("p"), "%d", &port)
	path := q.Get("path")
	if path == "" {
		path = "/"
	}
	body, ct, err := PreviewFetch(host, port, path)
	if err != nil {
		http.Error(w, "preview: "+err.Error(), 400)
		return
	}
	if ct != "" {
		w.Header().Set("Content-Type", ct)
	} else {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	}
	w.Write([]byte(body))
}
