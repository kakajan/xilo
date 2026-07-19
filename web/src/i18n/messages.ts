import type { AbstractIntlMessages } from "next-intl";
import type { Locale } from "./config";

import faCommon from "./messages/fa/common.json";
import faAuth from "./messages/fa/auth.json";
import faPost from "./messages/fa/post.json";
import faComment from "./messages/fa/comment.json";
import faDashboard from "./messages/fa/dashboard.json";
import faNotification from "./messages/fa/notification.json";
import faSettings from "./messages/fa/settings.json";

import enCommon from "./messages/en/common.json";
import enAuth from "./messages/en/auth.json";
import enPost from "./messages/en/post.json";
import enComment from "./messages/en/comment.json";
import enDashboard from "./messages/en/dashboard.json";
import enNotification from "./messages/en/notification.json";
import enSettings from "./messages/en/settings.json";

import arCommon from "./messages/ar/common.json";
import arAuth from "./messages/ar/auth.json";
import arPost from "./messages/ar/post.json";
import arComment from "./messages/ar/comment.json";
import arDashboard from "./messages/ar/dashboard.json";
import arNotification from "./messages/ar/notification.json";
import arSettings from "./messages/ar/settings.json";

import ruCommon from "./messages/ru/common.json";
import ruAuth from "./messages/ru/auth.json";
import ruPost from "./messages/ru/post.json";
import ruComment from "./messages/ru/comment.json";
import ruDashboard from "./messages/ru/dashboard.json";
import ruNotification from "./messages/ru/notification.json";
import ruSettings from "./messages/ru/settings.json";

import trCommon from "./messages/tr/common.json";
import trAuth from "./messages/tr/auth.json";
import trPost from "./messages/tr/post.json";
import trComment from "./messages/tr/comment.json";
import trDashboard from "./messages/tr/dashboard.json";
import trNotification from "./messages/tr/notification.json";
import trSettings from "./messages/tr/settings.json";

function pack(
  common: AbstractIntlMessages,
  auth: AbstractIntlMessages,
  post: AbstractIntlMessages,
  comment: AbstractIntlMessages,
  dashboard: AbstractIntlMessages,
  notification: AbstractIntlMessages,
  settings: AbstractIntlMessages
): AbstractIntlMessages {
  return { common, auth, post, comment, dashboard, notification, settings };
}

export const messagesByLocale: Record<Locale, AbstractIntlMessages> = {
  fa: pack(faCommon, faAuth, faPost, faComment, faDashboard, faNotification, faSettings),
  en: pack(enCommon, enAuth, enPost, enComment, enDashboard, enNotification, enSettings),
  ar: pack(arCommon, arAuth, arPost, arComment, arDashboard, arNotification, arSettings),
  ru: pack(ruCommon, ruAuth, ruPost, ruComment, ruDashboard, ruNotification, ruSettings),
  tr: pack(trCommon, trAuth, trPost, trComment, trDashboard, trNotification, trSettings),
};

export function getMessages(locale: Locale): AbstractIntlMessages {
  return messagesByLocale[locale];
}
