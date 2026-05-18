#!/bin/bash
# Download fonts for Xilo mobile app
# Run this script from the mobile/ directory

FONT_DIR="assets/fonts"
mkdir -p "$FONT_DIR"

echo "Downloading Vazirmatn (Persian font)..."
VAZIR_URL="https://github.com/rastikerdar/vazirmatn/releases/download/v33.003/Vazirmatn-v33.003.zip"
VAZIR_TMP=$(mktemp -d)
curl -L -o "$VAZIR_TMP/vazir.zip" "$VAZIR_URL" 2>/dev/null
if [ -f "$VAZIR_TMP/vazir.zip" ] && unzip -q "$VAZIR_TMP/vazir.zip" -d "$VAZIR_TMP" 2>/dev/null; then
  find "$VAZIR_TMP" -name "Vazirmatn-Regular.ttf" -exec cp {} "$FONT_DIR/" \;
  find "$VAZIR_TMP" -name "Vazirmatn-Medium.ttf" -exec cp {} "$FONT_DIR/" \;
  find "$VAZIR_TMP" -name "Vazirmatn-Bold.ttf" -exec cp {} "$FONT_DIR/" \;
  echo "  Vazirmatn downloaded."
else
  echo "  Failed to download Vazirmatn. Download manually from: $VAZIR_URL"
fi
rm -rf "$VAZIR_TMP"

echo "Downloading Noto Sans Arabic..."
NOTO_URL="https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSansArabic/NotoSansArabic%5Bwdth%2Cwght%5D.ttf"
NOTO_TMP=$(mktemp -d)
curl -L -o "$NOTO_TMP/noto.ttf" "$NOTO_URL" 2>/dev/null
if [ -f "$NOTO_TMP/noto.ttf" ]; then
  cp "$NOTO_TMP/noto.ttf" "$FONT_DIR/NotoSansArabic-Regular.ttf"
  cp "$NOTO_TMP/noto.ttf" "$FONT_DIR/NotoSansArabic-Medium.ttf"
  cp "$NOTO_TMP/noto.ttf" "$FONT_DIR/NotoSansArabic-Bold.ttf"
  echo "  Noto Sans Arabic downloaded."
else
  echo "  Failed to download Noto Sans Arabic."
  echo "  Download from: https://fonts.google.com/noto/specimen/Noto+Sans+Arabic"
fi
rm -rf "$NOTO_TMP"

echo "Downloading Inter (Latin font)..."
INTER_URL="https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip"
INTER_TMP=$(mktemp -d)
curl -L -o "$INTER_TMP/inter.zip" "$INTER_URL" 2>/dev/null
if [ -f "$INTER_TMP/inter.zip" ] && unzip -q "$INTER_TMP/inter.zip" -d "$INTER_TMP" 2>/dev/null; then
  find "$INTER_TMP" -name "Inter-Regular.otf" -exec cp {} "$FONT_DIR/Inter-Regular.ttf" \;
  find "$INTER_TMP" -name "Inter-Medium.otf" -exec cp {} "$FONT_DIR/Inter-Medium.ttf" \;
  find "$INTER_TMP" -name "Inter-Bold.otf" -exec cp {} "$FONT_DIR/Inter-Bold.ttf" \;
  echo "  Inter downloaded."
else
  echo "  Failed to download Inter. Download manually from: $INTER_URL"
fi
rm -rf "$INTER_TMP"

echo ""
echo "Font setup complete. Files in $FONT_DIR:"
ls -la "$FONT_DIR" 2>/dev/null
