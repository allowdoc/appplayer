# Agent Guidelines & Repository Workflow

## Git & Version Control Rule
- **Mandatory GitHub Synchronization**: Every time an agent modifies, updates, or fixes code in this repository, it MUST commit all changed files and push the update to the GitHub repository (`git add .`, `git commit -m "<message>"`, `git push origin <branch>`).
- Always check that `.gitignore` prevents checking in large binaries (>100MB like `lib/android-all-10.jar`, `app.jar`, `app-patched.jar`, `.zip` archives).

## Architecture & Code Standards
1. **Zero VM / Zero QEMU**: All execution is in-process native JVM execution. No virtualization, emulators, or headless Android daemons.
2. **Zero App-Specific Hardcoding**: All fixes must be generic, clean Android framework/runner implementations (`android.content.res`, `android.view`, `android.os`, `IPackageManager`, `libcore`, `dexlib2`, `ASM`).
3. **Bytecode Integrity**: Any bytecode rewritten via ASM must compute frames (`ClassWriter.COMPUTE_FRAMES` or `COMPUTE_MAXS`) and preserve authentic type signatures.
4. **Performance Budgets**: Cold boot time must remain < 5.0 s (with warm cache < 2.0 s) and Process RAM footprint < 60 MB.
