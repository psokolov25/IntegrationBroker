#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/docs"
mkdir -p "$OUT_DIR"

BUNDLE_OUT="$OUT_DIR/integration-broker-docs.bundle.md"
MANIFEST_OUT="$OUT_DIR/integration-broker-docs.manifest.txt"

mapfile -t PLAYBOOK_FILES < <(find "$ROOT_DIR/docs/playbooks" -maxdepth 1 -name '*.md' | sort)
mapfile -t GUIDE_FILES < <(find "$ROOT_DIR/docs/guides" -maxdepth 1 -name '*.md' | sort)
mapfile -t ROADMAP_FILES < <(find "$ROOT_DIR/docs/roadmap" -maxdepth 1 -name '*.md' | sort)

INPUT_FILES=("$ROOT_DIR/README.md" "${PLAYBOOK_FILES[@]}" "${GUIDE_FILES[@]}" "${ROADMAP_FILES[@]}")

printf '# Integration Broker — Documentation Bundle\n\n' > "$BUNDLE_OUT"
printf '# Manifest\n' > "$MANIFEST_OUT"

for file in "${INPUT_FILES[@]}"; do
  rel_path="${file#"$ROOT_DIR/"}"
  printf '%s\n' "$rel_path" >> "$MANIFEST_OUT"
  printf '\n---\n\n## Source: `%s`\n\n' "$rel_path" >> "$BUNDLE_OUT"
  cat "$file" >> "$BUNDLE_OUT"
  printf '\n' >> "$BUNDLE_OUT"
done

if command -v pandoc >/dev/null 2>&1; then
  HTML_OUT="$OUT_DIR/integration-broker-docs.html"
  pandoc \
    --from gfm \
    --toc \
    --toc-depth=3 \
    --standalone \
    --metadata title="Integration Broker — Documentation" \
    --output "$HTML_OUT" \
    "$BUNDLE_OUT"
  echo "HTML экспортирован: $HTML_OUT"
else
  echo "Предупреждение: pandoc не найден. HTML экспорт пропущен, Markdown bundle создан." >&2
fi

echo "Markdown bundle экспортирован: $BUNDLE_OUT"
echo "Manifest экспортирован: $MANIFEST_OUT"
