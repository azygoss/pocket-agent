// SPDX-License-Identifier: GPL-3.0-or-later
package gateway
import "errors"
// P15: 10MB cap + 24h short URL.
const MaxUpload = 10 << 20
var ErrTooLarge = errors.New("upload >10MB rejected")
func CheckUpload(size int64) error { if size > MaxUpload { return ErrTooLarge }; return nil }
