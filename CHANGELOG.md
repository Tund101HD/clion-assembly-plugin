<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Advanced Assembly Changelog

## [Unreleased]

## [1.0.0] - 2026-05-25

Initial public release.

### Added

- **NASM language support** (Intel x86/x64) — `.nasm`, `.asm`, `.inc` (content-based detection for contested extensions)
- **MIPS language support** (MIPS32) — `.mips`, `.s`
- Syntax highlighting with customizable color schemes for both languages
- Context-aware completion: instructions, registers, labels, cross-file `extern` symbols
- Hover documentation for NASM (x86 base ISA, x87 FPU, SSE/SSE2/SSE4.1/SSE4.2, AVX/AVX2 VEX forms, BMI2, AES-NI, ZMM/XMM/YMM/K registers) and MIPS (MIPS32r2, branch-likely variants, full trap set, `sync`, TLB instructions)
- Structure view, brace matching, code folding, line-comment toggle, formatter
- Live templates for prologue/epilogue, syscalls, and common patterns
- Line markers for label references
- Cross-file label resolution: Goto Declaration, Find Usages, Rename, Goto Symbol
- Refactoring support — rename labels across the project
- 11 inspections: duplicate labels, unknown mnemonics, undefined symbols/operands, NASM operand size/arity/register-type mismatch, MIPS operand constraints, MIPS unaligned memory offset
- External assembler diagnostics integration (`nasm` / `mips-as`) with clickable `file:line` console links
- **Project generator** — *New → Project → Assembly CMake Project* creates a working CMake project, starter source file, run config, and `.gitignore`
- File templates and example showcase files in `docs/examples/`
- Dedicated NASM and MIPS run configuration types (independent of CMake); build outputs land in `<workDir>/.asm-build/` to keep the project root clean
- **Cross-architecture debug pipeline** via QEMU user-mode emulation — `qemu-mips-static -g <port>` for MIPS, `qemu-x86_64-static -g <port>` for NASM on ARM hosts
- Bundled multi-arch GDB integration; gutter line breakpoints in `.nasm` / `.mips` files
- Auto-created **(QEMU Server)** + **(GDB Debug)** run-config pair on project open
- DWARF symbol emission (`--gdwarf-2` for `mips-linux-gnu-as`, `-g` for `nasm`)
- WSL ↔ Windows source path mapping (`/mnt/c` ↔ `C:\`, `/mnt/d` ↔ `D:\`) so breakpoints resolve in native paths
- Settings UI under *Settings → Assembly* (General / Executables / Debugger)
- Per-tool path overrides and *Prefer QEMU for NASM debug* toggle
- **Verify Toolchain** button + launch-time preflight that reports each missing tool with the exact `apt install` line
- Transparent WSL toolchain detection (binds to CLion's registered WSL toolchain, not `wsl.exe` defaults)
- Auto-eviction of CLion's broken auto-created CMake Application config for MIPS targets

[Unreleased]: https://github.com/Tund101HD/clion-assembly-plugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Tund101HD/clion-assembly-plugin/releases/tag/v1.0.0
