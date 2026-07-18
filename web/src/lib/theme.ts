/** Platform theme palette — matches backend pkg/theme and ui-ux-spec §2. */

export interface ThemePalette {
  primary: string;
  primary_hover: string;
  primary_pressed: string;
  primary_surface: string;
  background: string;
  background_secondary: string;
  background_tertiary: string;
  text_primary: string;
  text_secondary: string;
  text_tertiary: string;
  border: string;
  border_strong: string;
  error: string;
  success: string;
  warning: string;
  bubble_own: string;
  bubble_others: string;
  chat_bubble_own: string;
  chat_bubble_others: string;
}

export interface PlatformTheme {
  light: ThemePalette;
  dark: ThemePalette;
}

export const DEFAULT_THEME: PlatformTheme = {
  light: {
    primary: "#1D9BF0",
    primary_hover: "#1A8CD8",
    primary_pressed: "#1A7BC5",
    primary_surface: "#E8F5FE",
    background: "#FFFFFF",
    background_secondary: "#F7F9FA",
    background_tertiary: "#EFF3F4",
    text_primary: "#0F1419",
    text_secondary: "#536471",
    text_tertiary: "#8295A3",
    border: "#EFF3F4",
    border_strong: "#CFD9DE",
    error: "#F4212E",
    success: "#00BA7C",
    warning: "#FFAD1F",
    bubble_own: "#E8F5FE",
    bubble_others: "#F7F9FA",
    chat_bubble_own: "#E8F5FE",
    chat_bubble_others: "#F0F2F5",
  },
  dark: {
    primary: "#1D9BF0",
    primary_hover: "#4DB8F5",
    primary_pressed: "#6BC9F7",
    primary_surface: "#1A2A3A",
    background: "#15202B",
    background_secondary: "#192734",
    background_tertiary: "#22303C",
    text_primary: "#E7E9EA",
    text_secondary: "#71767B",
    text_tertiary: "#536471",
    border: "#38444D",
    border_strong: "#4A5A66",
    error: "#F4212E",
    success: "#00BA7C",
    warning: "#FFAD1F",
    bubble_own: "#1E3A5F",
    bubble_others: "#2C2C2E",
    chat_bubble_own: "#1E3A5F",
    chat_bubble_others: "#2C2C2E",
  },
};

const HEX_RE = /^#[0-9A-Fa-f]{6}$/;

export function isHexColor(value: string | null | undefined): boolean {
  if (typeof value !== "string") return false;
  return HEX_RE.test(value.trim());
}

/** Fill missing palette keys from defaults (older API payloads omit newer fields). */
export function mergeTheme(partial?: Partial<PlatformTheme> | null): PlatformTheme {
  return {
    light: { ...DEFAULT_THEME.light, ...(partial?.light ?? {}) },
    dark: { ...DEFAULT_THEME.dark, ...(partial?.dark ?? {}) },
  };
}

/** Map palette fields onto --xilo-* CSS variables consumed by globals.css. */
export function applyThemeToDocument(theme: PlatformTheme): void {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  setPaletteVars(root, "xilo", theme.light);
  setPaletteVars(root, "xilo-dark", theme.dark);
}

function setPaletteVars(root: HTMLElement, prefix: string, p: ThemePalette): void {
  root.style.setProperty(`--${prefix}-background`, p.background);
  root.style.setProperty(`--${prefix}-foreground`, p.text_primary);
  root.style.setProperty(`--${prefix}-card`, p.background);
  root.style.setProperty(`--${prefix}-card-foreground`, p.text_primary);
  root.style.setProperty(`--${prefix}-popover`, p.background);
  root.style.setProperty(`--${prefix}-popover-foreground`, p.text_primary);
  root.style.setProperty(`--${prefix}-primary`, p.primary);
  root.style.setProperty(`--${prefix}-primary-foreground`, "#FFFFFF");
  root.style.setProperty(`--${prefix}-secondary`, p.background_secondary);
  root.style.setProperty(`--${prefix}-secondary-foreground`, p.text_primary);
  root.style.setProperty(`--${prefix}-muted`, p.background_tertiary);
  root.style.setProperty(`--${prefix}-muted-foreground`, p.text_secondary);
  root.style.setProperty(`--${prefix}-accent`, p.primary_surface);
  root.style.setProperty(`--${prefix}-accent-foreground`, p.text_primary);
  root.style.setProperty(`--${prefix}-destructive`, p.error);
  root.style.setProperty(`--${prefix}-border`, p.border);
  root.style.setProperty(`--${prefix}-input`, p.border);
  root.style.setProperty(`--${prefix}-ring`, p.primary);
  root.style.setProperty(`--${prefix}-bubble-own`, p.bubble_own);
  root.style.setProperty(`--${prefix}-bubble-others`, p.bubble_others);
  root.style.setProperty(`--${prefix}-chat-bubble-own`, p.chat_bubble_own);
  root.style.setProperty(`--${prefix}-chat-bubble-others`, p.chat_bubble_others);
}

export const THEME_FIELD_LABELS: { key: keyof ThemePalette; label: string }[] = [
  { key: "primary", label: "رنگ اصلی" },
  { key: "primary_hover", label: "هاور اصلی" },
  { key: "primary_pressed", label: "فشرده اصلی" },
  { key: "primary_surface", label: "سطح اصلی" },
  { key: "background", label: "پس‌زمینه" },
  { key: "background_secondary", label: "پس‌زمینه ثانویه" },
  { key: "background_tertiary", label: "سطح / چیپ‌ها" },
  { key: "text_primary", label: "متن اصلی" },
  { key: "text_secondary", label: "متن ثانویه" },
  { key: "text_tertiary", label: "متن کم‌رنگ" },
  { key: "border", label: "حاشیه" },
  { key: "border_strong", label: "حاشیه قوی" },
  { key: "error", label: "خطا" },
  { key: "success", label: "موفقیت" },
  { key: "warning", label: "هشدار" },
  { key: "bubble_own", label: "حباب خودم" },
  { key: "bubble_others", label: "حباب دیگران" },
  { key: "chat_bubble_own", label: "حباب چت خودم" },
  { key: "chat_bubble_others", label: "حباب چت دیگران" },
];
