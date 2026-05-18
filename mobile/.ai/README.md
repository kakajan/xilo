# AI Development Assistant

> راهنمای هوش مصنوعی برای توسعه سریع‌تر اپلیکیشن Xilo Mobile

## 🚀 شروع سریع

1. ابتدا `../AGENTS.md` را بخوان (قوانین کلی پروژه)
2. سپس `skills/INDEX.md` را بررسی کن (لیست skillها)
3. skill مرتبط با کار فعلی را از `skills/` بخوان

## 📁 ساختار

```
.ai/
├── README.md           # این فایل — نقطه شروع
└── skills/
    ├── INDEX.md        # لیست همه skillها
    ├── riverpod.md     # State management
    ├── freezed-models.md # Data models
    ├── dio-networking.md # HTTP/API
    ├── gorouter-navigation.md # Routing
    ├── hive-storage.md # Local storage
    ├── ui-widgets.md   # UI components
    ├── testing.md      # Tests
    ├── websocket-realtime.md # Real-time
    ├── service-locator.md # DI
    └── theme-styling.md # Theme
```

## 🎯 چگونه از skillها استفاده کنی

1. **تشخیص نوع کار**: ببین چه چیزی می‌خواهی بسازی
2. **خواندن skill**: فایل مرتبط را از `skills/` بخوان
3. **استفاده از template**: از الگوهای داخل skill استفاده کن
4. **پیروی از قوانین**: قوانین MUST را رعایت کن

## 📋 مثال‌های کاربردی

### ساخت Feature جدید
```
1. skills/freezed-models.md → مدل domain
2. skills/dio-networking.md → remote data source
3. skills/hive-storage.md → local data source
4. skills/service-locator.md → ثبت در DI
5. skills/riverpod.md → provider
6. skills/ui-widgets.md → screen + widgets
7. skills/gorouter-navigation.md → route
8. skills/testing.md → tests
```

### تغییر در State
```
skills/riverpod.md → الگوی provider
```

### افزودن API جدید
```
skills/dio-networking.md → الگوی remote data source
```

### افزودن صفحه جدید
```
skills/ui-widgets.md → الگوی screen
skills/gorouter-navigation.md → route
```

## ⚡ نکات کاهش توکن

- از templateهای داخل skillها استفاده کن (کپی + تغییر)
- فقط بخش‌های مرتبط را بخوان (نه کل فایل)
- از INDEX.md برای پیدا کردن سریع skill استفاده کن
- الگوهای تکراری را یک بار بنویس، بارها استفاده کن

## 📞 ارتباط با مستندات اصلی

- `../AGENTS.md` — قوانین کلی پروژه
- `../pubspec.yaml` — dependencies
- `../lib/` — کد اصلی
- `../test/` — تست‌ها
