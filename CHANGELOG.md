<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Advanced Assembly Changelog

## [Unreleased]

## [1.0.1] - 2026-05-27

Hotfix release. The initial Marketplace upload was built from a pre-fix snapshot of the source tree, so users installing v1.0.0 on top of an existing CLion configuration hit a `Unknown bundled file template` assertion that prevented the plugin from loading. This release re-packages the post-fix tree and bundles the other late-cycle improvements that missed the original zip.

### Fixed

- **Plugin failed to load on existing CLion installs** — `<internalFileTemplate>` entries for `NASM Assembly File` and `MIPS Assembly File` are now registered in `plugin.xml`, satisfying the IntelliJ platform's bundled-template lookup. (Crash signature: `Assertion failed: Unknown bundled file template`.)
- **MIPS file template rendered literal `${'}`** in the new-file template body — Velocity `#set ($DOLLAR = …)` is now used to emit the dollar sign cleanly.
- **External annotator no longer self-disables when the PSI parse tree contains any error** — removed the `hasErrors` short-circuit in both `NasmExternalAnnotator` and `MipsExternalAnnotator`, so semantic diagnostics from `nasm` / `mips-as` continue to surface in files that already have other red squiggles.
- **`-w-all` warning suppression removed** from the nasm command line; `nasm` overflow and `byte data exceeds bounds` warnings now reach the editor as expected.
- **`docs/examples/nasm-showcase.nasm`** — the `done` label was incorrectly written as `.done`, scoping it as a local label under the previous global. Renamed to `done` so the showcase parses cleanly.
- **CMake project generator** now emits `-no-pie` in `linkFlags` for ELF formats, and the standalone NASM run-config build pipeline passes `-no-pie` to `gcc` for `elf32` / `elf64`. Resolves *"relocation R_X86_64_32 against `.text' can not be used when making a PIE object"* on modern Ubuntu where PIE is the default.

### Added

- **External annotator now auto-resolves `nasm` and `mips-as`** without a configured path: tries the user's configured absolute path → host `PATH` → common Windows install dirs (`Program Files\NASM`, `%LOCALAPPDATA%\Programs\NASM`) → `wsl.exe -- command -v <name>` inside the registered WSL distro. Tools located inside WSL are invoked through `wslExe()` with `/mnt/c`-style path mapping for the tempfile.
- **NASM grammar** accepts unary minus (`-1`, `-0x10`) and backtick strings (` `\n` `) in addition to single- and double-quoted forms.
- README now documents nasm's two-pass behavior so users understand why semantic warnings disappear once a hard error is introduced earlier in the file.

### Changed

- **Plugin repository metadata repointed** from the old `clion-assembly-plugin` repo to the public `clion-nasm` repo — `pluginRepositoryUrl` in `gradle.properties`, `repositoryUrl` in `build.gradle.kts` (drives the changelog plugin and the Marketplace "Source Code" link), plus `README.md` and `CHANGELOG.md` link footers.

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

[Unreleased]: https://github.com/Tund101HD/clion-nasm/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/Tund101HD/clion-nasm/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/Tund101HD/clion-nasm/releases/tag/v1.0.0
