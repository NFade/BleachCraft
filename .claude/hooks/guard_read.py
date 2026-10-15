"""PreToolUse hook for Read: blocks reading a big text file without offset/limit (exit code 2 = block, the message goes to the model).

Settings: .claude/settings.json -> hooks.PreToolUse (matcher Read). Reads JSON from stdin: {"tool_name": "Read", "tool_input": {...}}.
Any error in this hook never blocks work (it exits 0).
"""
import json
import os
import sys

LIMIT_BYTES = 40 * 1024
TEXT_EXT = (".md", ".java", ".json", ".txt", ".log", ".py", ".gradle", ".yml", ".yaml", ".xml", ".csv", ".obj", ".mtl", ".patch", ".toml", ".properties")


def main():
	try:
		data = json.load(sys.stdin)
		inp = data.get("tool_input") or {}
		path = inp.get("file_path") or ""
		if not path.lower().endswith(TEXT_EXT) or not os.path.isfile(path):
			return 0
		size = os.path.getsize(path)
		if size <= LIMIT_BYTES:
			return 0
		if inp.get("limit") is not None or inp.get("offset") is not None:
			return 0
		sys.stderr.write(
			"Read blocked: %s is %d KB. Read it in parts with offset/limit (about 80-200 lines), or find the section first "
			"(design/INDEX.md for the design documents, Grep -n for a symbol). Token economy rule, see CLAUDE.md.\n" % (os.path.basename(path), size // 1024))
		return 2
	except Exception:
		return 0


if __name__ == "__main__":
	sys.exit(main())
