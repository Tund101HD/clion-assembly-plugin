# Advanced Assembly for CLion

[![Build](https://github.com/Tund101HD/clion-nasm/actions/workflows/build.yml/badge.svg)](https://github.com/Tund101HD/clion-nasm/actions/workflows/build.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

First-class **NASM** (Intel x86/x64) and **MIPS** (MIPS32) language support for JetBrains CLion — full editor intelligence, project generation, build pipeline, and a working cross-architecture debugger backed by QEMU user-mode emulation.

Built for students, hobbyists, and engineers who want to write assembly with the same comfort they get from CLion's C/C++ workflows: navigation, completion, inspections, refactors, gutter breakpoints, and step-through debugging — without leaving the IDE.

> **Full feature tour and walkthroughs:** [lucidev.me/tools/clion-assembly-plugin](https://lucidev.me/tools/clion-assembly-plugin)

---

## Highlights

**Editor**
- Syntax highlighting with customizable color schemes for both languages
- Context-aware completion (instructions, registers, labels, `extern` symbols)
- Hover documentation: x86 + SSE/AVX/AVX2/BMI2/AES for NASM; MIPS32r2 + branch-likely + traps + TLB for MIPS
- Structure view, brace matching, code folding, line-comment toggle, live templates

**Navigation & refactoring**
- Cross-file label resolution: Goto Declaration, Find Usages, Rename, Goto Symbol
- Line markers for label references

**Inspections (11 total)**
- Duplicate labels, unknown mnemonics, undefined symbols
- NASM operand size / arity / register-type mismatch
- MIPS operand constraints, unaligned memory offsets
- External diagnostics surfaced from `nasm` / `mips-as` with clickable `file:line` console links

> Live external diagnostics mirror what the underlying assembler emits on the command line. nasm/mips-as run in multiple passes and abort after the first hard error, so semantic warnings (e.g. *"byte data exceeds bounds"*) only surface once the red-squiggle errors above them are fixed — same as `gcc` or any other two-pass toolchain.

**Project generation**
- *New → Project → Assembly CMake Project* — pick NASM or MIPS, get a working CMake project with a starter source file, run config, and `.gitignore` pre-wired

**Run & Debug**
- Dedicated run configurations for NASM and MIPS binaries (no CMake required)
- Cross-arch debug via QEMU: `qemu-mips-static` for MIPS, `qemu-x86_64-static` for NASM on ARM hosts
- Gutter breakpoints in `.nasm` / `.mips` files; step through assembly source with bundled multi-arch GDB
- Auto-created **(QEMU Server)** + **(GDB Debug)** run-config pair on project open
- One-click *Verify Toolchain* + launch-time preflight that points at missing `apt install` packages

**Windows / WSL**
- Transparent WSL toolchain integration: detects your distro, runs the assembler/linker/emulator inside WSL, maps `/mnt/c` ↔ `C:\` so breakpoints resolve in native Windows source paths

---

## Install

**From JetBrains Marketplace** (recommended)
1. CLion → *Settings* → *Plugins* → *Marketplace*
2. Search for `Advanced Assembly`
3. Install and restart

**From disk**
1. Download the latest `.zip` from [Releases](https://github.com/Tund101HD/clion-nasm/releases)
2. CLion → *Settings* → *Plugins* → ⚙ → *Install Plugin from Disk…*

---

## Quickstart

After installing, create your first project in under a minute:

1. *File → New → Project → Assembly CMake Project*
2. Pick **NASM** or **MIPS** as the language, name it, hit *Create*
3. CLion finishes the CMake reload and creates a `<name>` run config (and `(QEMU Server)` + `(GDB Debug)` pair on supported hosts)
4. Press ▶ to build and run, or 🐞 on the *(GDB Debug)* config to step through assembly with breakpoints

The starter file contains a working "hello world" syscall sequence for the chosen architecture — assemble, link, run, debug, all wired up.

---

## Requirements

| Need | Tool | Install |
|---|---|---|
| NASM run/build | `nasm` | `apt install nasm` / `brew install nasm` |
| NASM debug on ARM hosts | `qemu-x86_64-static`, `gdb-multiarch` | `apt install qemu-user-static gdb-multiarch` |
| MIPS run/build | `mips-linux-gnu-as`, `mips-linux-gnu-ld` | `apt install binutils-mips-linux-gnu` |
| MIPS run | `qemu-mips-static` | `apt install qemu-user-static` |
| MIPS debug | `qemu-mips-static` + bundled CLion GDB | (GDB ships with CLion 2026.1+) |

**Windows users**: install everything *inside* WSL — the plugin auto-detects your distro from CLion's WSL toolchain. *Settings → Assembly → Executables → Verify Toolchain* checks every required binary and tells you the exact `apt install` line for anything missing.

---

## Compatibility

- **IDE:** CLion 2026.1.1+ (build 261+)
- **OS:** Linux, macOS (NASM only — MIPS supported, NASM debug deferred on macOS), Windows + WSL
- **License:** Apache 2.0

---

## Building from source

```bash
./gradlew generateNasmLexer generateNasmParser generateMipsLexer generateMipsParser
./gradlew buildPlugin
```

The first command generates PSI/lexer classes from the JFlex and Grammar-Kit sources in `src/main/{jflex,grammar}/` into `src/main/gen/` (which is git-ignored). If your IDE shows unresolved references for `MipsTypes`, `NasmLabelDef`, or similar generated types after a fresh clone, run that command and re-index. `./gradlew runIde` launches a sandbox CLion with the plugin loaded for manual testing.

---

## Links

- 🌐 [Plugin page on lucidev.me](https://lucidev.me/tools/clion-assembly-plugin) — feature tour, walkthroughs, screenshots
- 🏪 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31969-advanced-assembly)
- 🐛 [Issue tracker (YouTrack)](https://lucaperri.youtrack.cloud/projects/CAP/issues) — report bugs and feature requests
- 📜 [Changelog](CHANGELOG.md)

---

## License

Released under the [Apache License 2.0](LICENSE). Copyright © 2026 Luca Perri.
