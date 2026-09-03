// SPDX-License-Identifier: GPL-3.0-or-later
package store

import (
	"testing"
	"time"
)

func TestUploadTTLSweep(t *testing.T) {
	s := New()
	now := time.Now()
	_ = s.PutUpload("t1", Upload{ID: "u1", TenantID: "t1", ShortCode: "s1", Size: 10, CreatedAt: now.Add(-25 * time.Hour), ExpiresAt: now.Add(-time.Hour)})
	_ = s.PutUpload("t1", Upload{ID: "u2", TenantID: "t1", ShortCode: "s2", Size: 10, CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
	s.SweepTTL(now)
	if _, ok := s.GetUploadByShort("s1", now.Unix()); ok {
		t.Fatal("expired upload must be swept")
	}
	if _, ok := s.GetUploadByShort("s2", now.Unix()); !ok {
		t.Fatal("fresh upload must remain")
	}
	if err := s.DeleteUpload("t1", "u2"); err != nil {
		t.Fatal(err)
	}
}
