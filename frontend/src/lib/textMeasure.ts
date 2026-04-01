import { layout, prepare, prepareWithSegments, walkLineRanges } from "@chenglou/pretext";

const preparedCache = new Map<string, ReturnType<typeof prepare>>();
const preparedSegmentsCache = new Map<string, ReturnType<typeof prepareWithSegments>>();

const DEFAULT_FONT_SIZE = 15;
const DEFAULT_LINE_HEIGHT = 24;
const BUBBLE_HORIZONTAL_PADDING = 40;
const BUBBLE_VERTICAL_PADDING = 28;
const MESSAGE_GAP = 24;
const MIN_BUBBLE_WIDTH = 148;

interface HeightEstimateOptions {
  includesCards?: boolean;
}

function getCacheKey(text: string, font: string, whiteSpace: "normal" | "pre-wrap") {
  return `${font}|${whiteSpace}|${text}`;
}

function getFontFamilyFromBody() {
  if (typeof window === "undefined") {
    return '"Segoe UI", "PingFang SC", sans-serif';
  }
  return window.getComputedStyle(document.body).fontFamily || '"Segoe UI", sans-serif';
}

function getPreparedText(text: string, font: string, whiteSpace: "normal" | "pre-wrap" = "normal") {
  const key = getCacheKey(text, font, whiteSpace);
  const cached = preparedCache.get(key);
  if (cached) {
    return cached;
  }
  const prepared = prepare(text, font, { whiteSpace });
  preparedCache.set(key, prepared);
  return prepared;
}

function getPreparedTextWithSegments(
  text: string,
  font: string,
  whiteSpace: "normal" | "pre-wrap" = "normal"
) {
  const key = getCacheKey(text, font, whiteSpace);
  const cached = preparedSegmentsCache.get(key);
  if (cached) {
    return cached;
  }
  const prepared = prepareWithSegments(text, font, { whiteSpace });
  preparedSegmentsCache.set(key, prepared);
  return prepared;
}

function getFontShorthand(fontSizePx = DEFAULT_FONT_SIZE, fontWeight = 400) {
  return `${fontWeight} ${fontSizePx}px ${getFontFamilyFromBody()}`;
}

function getFallbackLineCount(text: string, maxWidth: number) {
  const charsPerLine = Math.max(8, Math.floor(maxWidth / 7.8));
  const explicitLines = text.split("\n");
  return explicitLines.reduce((sum, line) => {
    if (!line) {
      return sum + 1;
    }
    return sum + Math.max(1, Math.ceil(line.length / charsPerLine));
  }, 0);
}

function getFallbackTextHeight(text: string, maxWidth: number, lineHeight: number) {
  return getFallbackLineCount(text, maxWidth) * lineHeight;
}

export function isComplexMarkdown(text: string) {
  return /```|`[^`]+`|^\s{0,3}#{1,6}\s|^\s*([-*+]\s|\d+\.\s)|^\s*>\s|\|.*\||!\[[^\]]*\]\([^)]+\)|\[[^\]]+\]\([^)]+\)/m.test(
    text
  );
}

export function computeShrinkBubbleWidth(text: string, maxBubbleWidthPx: number) {
  if (!text.trim() || maxBubbleWidthPx <= 0 || isComplexMarkdown(text)) {
    return undefined;
  }

  const contentMaxWidth = Math.max(100, maxBubbleWidthPx - BUBBLE_HORIZONTAL_PADDING);
  const minContentWidth = Math.min(MIN_BUBBLE_WIDTH, contentMaxWidth);
  const lineHeight = DEFAULT_LINE_HEIGHT;
  const font = getFontShorthand();

  try {
    const prepared = getPreparedText(text, font, "normal");
    const baseline = layout(prepared, contentMaxWidth, lineHeight);
    const allowedLineCount = baseline.lineCount <= 2 ? baseline.lineCount : baseline.lineCount + 1;

    let low = minContentWidth;
    let high = contentMaxWidth;
    let best = contentMaxWidth;

    while (high - low > 2) {
      const mid = (low + high) / 2;
      const current = layout(prepared, mid, lineHeight);
      if (current.lineCount <= allowedLineCount) {
        best = mid;
        high = mid;
      } else {
        low = mid;
      }
    }

    let widestLine = 0;
    const preparedSegments = getPreparedTextWithSegments(text, font, "normal");
    walkLineRanges(preparedSegments, best, (line) => {
      if (line.width > widestLine) {
        widestLine = line.width;
      }
    });

    const safeContentWidth = Math.max(minContentWidth, Math.min(best, Math.ceil(widestLine + 2)));
    const totalWidth = Math.min(
      maxBubbleWidthPx,
      Math.max(MIN_BUBBLE_WIDTH, safeContentWidth + BUBBLE_HORIZONTAL_PADDING)
    );

    return Math.round(totalWidth);
  } catch {
    const fallbackLines = getFallbackLineCount(text, contentMaxWidth);
    const roughLineWidth = Math.min(contentMaxWidth, Math.max(88, text.length * 7.2 / fallbackLines));
    return Math.round(roughLineWidth + BUBBLE_HORIZONTAL_PADDING);
  }
}

export function estimateMessageRowHeight(
  text: string,
  maxBubbleWidthPx: number,
  options: HeightEstimateOptions = {}
) {
  const lineHeight = DEFAULT_LINE_HEIGHT;
  const contentMaxWidth = Math.max(120, maxBubbleWidthPx - BUBBLE_HORIZONTAL_PADDING);

  let textHeight = lineHeight;
  if (text.trim()) {
    if (isComplexMarkdown(text)) {
      const codeBlocks = (text.match(/```/g) || []).length / 2;
      const roughHeight = getFallbackTextHeight(text, contentMaxWidth, lineHeight);
      textHeight = roughHeight + codeBlocks * lineHeight * 3;
    } else {
      try {
        const prepared = getPreparedText(text, getFontShorthand(), "normal");
        textHeight = layout(prepared, contentMaxWidth, lineHeight).height;
      } catch {
        textHeight = getFallbackTextHeight(text, contentMaxWidth, lineHeight);
      }
    }
  }

  let totalHeight = textHeight + BUBBLE_VERTICAL_PADDING + MESSAGE_GAP;
  if (options.includesCards) {
    totalHeight += 260;
  }
  return Math.max(92, Math.ceil(totalHeight));
}
