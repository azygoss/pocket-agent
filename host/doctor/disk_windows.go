// SPDX-License-Identifier: GPL-3.0-or-later
//go:build windows

package doctor

// diskFree: Windows'ta statfs yok — ölçülemedi olarak geç (Unix'teki
// hata yoluyla aynı davranış; doctor fail etmez).
func diskFree(home string) Check {
	return Check{Name: "disk", OK: true, Info: "ölçülemedi"}
}
