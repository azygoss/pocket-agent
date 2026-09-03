// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"testing"
)

func TestPreviewLoopbackOnly(t *testing.T) {
	if _, _, err := PreviewFetch("example.com", 80, "/"); err == nil {
		t.Fatal("public host must fail")
	}
	if _, _, err := PreviewFetch("169.254.169.254", 80, "/"); err == nil {
		t.Fatal("metadata IP must fail")
	}
	// live loopback server
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html")
		w.Write([]byte("<h1>hi</h1>"))
	}))
	defer srv.Close()
	u, _ := url.Parse(srv.URL)
	port, _ := strconv.Atoi(u.Port())
	body, ct, err := PreviewFetch("127.0.0.1", port, "/")
	if err != nil || body != "<h1>hi</h1>" || ct != "text/html" {
		t.Fatalf("loopback fetch: %q %q %v", body, ct, err)
	}
}
