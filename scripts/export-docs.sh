#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/build/docs"
mkdir -p "$OUT_DIR"

if ! command -v pandoc >/dev/null 2>&1; then
  echo "Ошибка: pandoc не найден. Установите pandoc и повторите попытку." >&2
  exit 1
fi

mapfile -t PLAYBOOK_FILES < <(find "$ROOT_DIR/docs/playbooks" -maxdepth 1 -name '*.md' | sort)
mapfile -t GUIDE_FILES < <(find "$ROOT_DIR/docs/guides" -maxdepth 1 -name '*.md' | sort)
mapfile -t ROADMAP_FILES < <(find "$ROOT_DIR/docs/roadmap" -maxdepth 1 -name '*.md' | sort)

INPUT_FILES=("$ROOT_DIR/README.md" "${PLAYBOOK_FILES[@]}" "${GUIDE_FILES[@]}" "${ROADMAP_FILES[@]}")

DOCX_OUT="$OUT_DIR/integration-broker-docs.docx"
PDF_OUT="$OUT_DIR/integration-broker-docs.pdf"

pandoc \
  --from gfm \
  --toc \
  --toc-depth=3 \
  --metadata title="Integration Broker — Документация" \
  --output "$DOCX_OUT" \
  "${INPUT_FILES[@]}"

PDF_ENGINE=""
for candidate in wkhtmltopdf weasyprint xelatex; do
  if command -v "$candidate" >/dev/null 2>&1; then
    PDF_ENGINE="$candidate"
    break
  fi
done

if [[ -n "$PDF_ENGINE" ]]; then
  pandoc \
    --from gfm \
    --toc \
    --toc-depth=3 \
    --pdf-engine "$PDF_ENGINE" \
    --metadata title="Integration Broker — Документация" \
    --output "$PDF_OUT" \
    "${INPUT_FILES[@]}"
  echo "PDF экспортирован: $PDF_OUT"
else
  echo "Предупреждение: PDF-движок не найден (wkhtmltopdf/weasyprint/xelatex). DOCX создан, PDF пропущен." >&2
fi

echo "DOCX экспортирован: $DOCX_OUT"
