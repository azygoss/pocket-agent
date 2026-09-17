// SPDX-License-Identifier: GPL-3.0-or-later
"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { binaryName } = require("../bin/pocket-agent.js");

test("binaryName maps all five supported platform/arch pairs", () => {
  assert.equal(binaryName("linux", "x64"), "pocket-agent-linux-amd64");
  assert.equal(binaryName("linux", "arm64"), "pocket-agent-linux-arm64");
  assert.equal(binaryName("darwin", "x64"), "pocket-agent-darwin-amd64");
  assert.equal(binaryName("darwin", "arm64"), "pocket-agent-darwin-arm64");
  assert.equal(binaryName("win32", "x64"), "pocket-agent-windows-amd64.exe");
});

test("binaryName throws on unsupported pair", () => {
  assert.throws(() => binaryName("linux", "ia32"), /unsupported.*linux.*ia32/);
});
