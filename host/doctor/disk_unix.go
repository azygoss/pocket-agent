// SPDX-License-Identifier: GPL-3.0-or-later
//go:build unix

package doctor

import (
	"fmt"
	"syscall"
)

// diskFree: home dosya sisteminde boş alan (%5 altı veya <100MB fail).
func diskFree(home string) Check {
	var st syscall.Statfs_t
	if err := syscall.Statfs(home, &st); err != nil {
		return Check{Name: "disk", OK: true, Info: "ölçülemedi"}
	}
	free := st.Bavail * uint64(st.Bsize)
	total := st.Blocks * uint64(st.Bsize)
	pct := 0
	if total > 0 {
		pct = int(free * 100 / total)
	}
	ok := free > 100<<20 && pct > 5
	return Check{Name: "disk", OK: ok, Info: fmt.Sprintf("%.1fGiB boş (%%%d)", float64(free)/(1<<30), pct)}
}
