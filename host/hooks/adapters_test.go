// SPDX-License-Identifier: GPL-3.0-or-later
package hooks
import ("testing"; "strings")
func TestMergePreservesUser(t *testing.T) {
  user := "{\"user\": true}"
  merged := Merge(user, All()[0].Block)
  if !strings.Contains(merged, `"user": true`) { t.Fatal("user content lost") }
  if !strings.Contains(merged, "pocket-agent begin") { t.Fatal("block missing") }
  merged2 := Merge(merged, All()[0].Block)
  if strings.Count(merged2, "pocket-agent begin") != 1 { t.Fatal("reinstall must not duplicate") }
}
func TestTwelveAgents(t *testing.T) {
  if len(All()) != 12 { t.Fatalf("want 12 agents, got %d", len(All())) }
}
