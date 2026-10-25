#!/usr/bin/env bash
# probe.sh IMG — overall gray mean + dominant colors + top/mid/bottom band means
img="$1"
echo "== $img =="
echo -n "  gray mean: "; magick "$img" -colorspace gray -format "%[fx:round(255*mean)]\n" info:
echo "  dominant colors:"; magick "$img" +dither -colors 4 -format "%[fx:round(255*mean)] " info: ; magick "$img" +dither -colors 4 -unique-colors txt:- | tail -n +2 | awk '{printf "    %s %s\n", $(NF-1), $NF}' | head -4
for band in "10%x8%+0+4%" "10%x8%+0+46%" "10%x8%+0+88%"; do
  echo -n "  band($band): "; magick "$img" -crop "$band" -colorspace gray -format "%[fx:round(255*mean)] " info:; echo
done
