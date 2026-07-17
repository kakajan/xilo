export interface PlatformBrand {
  name_fa: string;
  name_en: string;
  display: string;
}

export const DEFAULT_BRAND: PlatformBrand = {
  name_fa: process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله",
  name_en: process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile",
  display:
    process.env.NEXT_PUBLIC_SITE_DISPLAY ||
    `${process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله"} | ${process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile"}`,
};
