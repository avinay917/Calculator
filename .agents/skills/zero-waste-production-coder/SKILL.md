---
name: zero-waste-production-coder
description: Strictly enforces AI token quota conservation and mandates 100% working, production-ready code with zero dummy/mock data, zero placeholders, and zero redundant inspection loops. Use whenever writing code, refactoring, adding features, or debugging.
---

# Zero-Waste Production Coder Skill

This skill enforces strict rules to **prevent AI token quota exhaustion** and **guarantee 100% functional, real, production-ready code** without any dummy or placeholder code.

---

## 1. AI Quota Conservation (Zero Token Waste)

1. **Direct Targeted Execution (No Exploration Loops)**:
   - Do NOT run repetitive `view_file`, `list_dir`, or `grep_search` calls when file paths, symbols, or method signatures are already known or documented in `GEMINI.md` / `CODE_MAP.md`.
   - Never inspect entire 500-line files when editing a 10-line function; read only the specific line range needed.
   - Use `replace_file_content` with minimal precise context rather than rewriting entire files.

2. **Single-Turn Action Batching**:
   - Do not split a single task into separate conversational turns ("I will inspect -> I will think -> I will edit -> I will ask").
   - Execute the necessary edits and build verification directly in one turn.

3. **Compact Explanations**:
   - Provide crisp, high-signal explanations in Hindi/Hinglish.
   - Do not recite back entire files or generate unnecessary boilerplate summaries.

---

## 2. 100% Working Production Code Law (Zero Dummy / Mock)

1. **NO Placeholders or Incomplete Implementations**:
   - **STRICTLY PROHIBITED**:
     - `// TODO: implement this later`
     - `/* Add your logic here */`
     - `fun doSomething() { /* pass */ }`
     - Returning fake or hardcoded mock objects (e.g. `listOf("dummy1", "dummy2")`) in production business logic.
   - Every single function, click listener, repository method, and UI component MUST be fully implemented with real logic.

2. **End-to-End Functional Completeness**:
   - If a button is added to the UI, the state, event handler, ViewModel method, and repository/database persistence MUST be completely wired end-to-end.
   - If an audio player is created, implement real seek, play, pause, release, and error callbacks, not stub methods.
   - If a background service or receiver is created, wire all intents, wake locks, and lifecycle release methods.

3. **No Breaking Changes & Backward Compatibility**:
   - Never break existing working methods, database structures, or UI components to add a quick shortcut.
   - All newly added model fields must have default parameters so older app versions do not crash.

---

## 3. Pre-Flight Verification Checklist
Before submitting code to the user:
- [x] Are there any `TODO` comments or empty stub functions? (If yes, implement them immediately).
- [x] Is there any dummy / placeholder data in production flows? (If yes, replace with real models/database logic).
- [x] Was token consumption kept to an absolute minimum?
- [x] Is the code syntax-checked, verified, and ready to deploy?
