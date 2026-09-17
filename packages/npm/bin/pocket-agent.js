#!/usr/bin/env node
// SPDX-License-Identifier: GPL-3.0-or-later
// pocket-agent host CLI: platform binary'sini vendor/ altından çalıştırır.
"use strict";
const path = require("path");
const { spawn } = require("child_process");

const BINARIES = {
  "linux-x64": "pocket-agent-linux-amd64",
  "linux-arm64": "pocket-agent-linux-arm64",
  "darwin-x64": "pocket-agent-darwin-amd64",
  "darwin-arm64": "pocket-agent-darwin-arm64",
  "win32-x64": "pocket-agent-windows-amd64.exe",
};

function binaryName(platform, arch) {
  const name = BINARIES[`${platform}-${arch}`];
  if (!name) {
    throw new Error(
      `pocket-agent: unsupported platform ${platform}/${arch} ` +
        `(supported: ${Object.keys(BINARIES).join(", ")})`
    );
  }
  return name;
}

module.exports = { binaryName };

if (require.main === module) {
  const bin = path.join(
    __dirname, "..", "vendor", binaryName(process.platform, process.arch)
  );
  const child = spawn(bin, process.argv.slice(2), { stdio: "inherit" });

  // Unix'te sinyaller child'a iletilir; child sinyalle ölürse kendi
  // dinleyicilerimizi kaldırıp sinyali kendimize yollayarak çıkış şeklini koruruz.
  const handlers = {};
  if (process.platform !== "win32") {
    for (const sig of ["SIGINT", "SIGTERM", "SIGHUP"]) {
      handlers[sig] = () => child.kill(sig);
      process.on(sig, handlers[sig]);
    }
  }

  child.on("error", (err) => {
    console.error(`pocket-agent: ${err.message}`);
    process.exitCode = 1;
  });
  child.on("exit", (code, signal) => {
    if (signal && process.platform !== "win32") {
      for (const sig of Object.keys(handlers)) {
        process.removeListener(sig, handlers[sig]);
      }
      process.kill(process.pid, signal);
      process.exitCode = 1; // sinyal yutulursa fallback
      return;
    }
    if (code !== null) process.exitCode = code;
  });
}
