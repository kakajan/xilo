# Skill: Freezed Models

> Use when creating or modifying data models with Freezed + JSON serialization

## Rules

1. ALL data models MUST use `@freezed`
2. ALL models MUST have `fromJson`/`toJson` via `@jsonSerializable`
3. File naming: `<model>.dart` with `part` files
4. Use `@Default` for optional fields with defaults
5. Use `@JsonKey(name: 'snake_case')` for API field mapping

## Template

```dart
import 'package:freezed_annotation/freezed_annotation.dart';

part '<model>.freezed.dart';
part '<model>.g.dart';

@freezed
class <Model> with _$<Model> {
  const factory <Model>({
    required String id,
    required String name,
    @Default(false) bool isActive,
    @Default([]) List<String> tags,
    DateTime? createdAt,
  }) = _<Model>;

  factory <Model>.fromJson(Map<String, dynamic> json) =>
      _$<Model>FromJson(json);
}
```

## Common Patterns

### Nested Models

```dart
@freezed
class Author with _$Author {
  const factory Author({
    required String id,
    required String name,
    String? avatarUrl,
  }) = _Author;

  factory Author.fromJson(Map<String, dynamic> json) =>
      _$AuthorFromJson(json);
}

@freezed
class Post with _$Post {
  const factory Post({
    required String id,
    required String title,
    required Author author,
    @Default([]) List<String> tags,
  }) = _Post;

  factory Post.fromJson(Map<String, dynamic> json) =>
      _$PostFromJson(json);
}
```

### Union Types (Sealed Classes)

```dart
@freezed
class AuthState with _$AuthState {
  const factory AuthState.initial() = _Initial;
  const factory AuthState.loading() = _Loading;
  const factory AuthState.authenticated(User user) = _Authenticated;
  const factory AuthState.error(String message) = _Error;
}
```

### Copy With

```dart
final updated = post.copyWith(title: 'New Title');
```

### Equality (Built-in)

```dart
post1 == post2  // Value equality, not reference
```

## Commands

```bash
# Generate .freezed.dart and .g.dart files
flutter pub run build_runner build --delete-conflicting-outputs

# Watch mode for continuous generation
flutter pub run build_runner watch --delete-conflicting-outputs
```

## Field Types Reference

| Type | Annotation | Example |
|------|-----------|---------|
| Required | `required` | `required String id` |
| Optional | `?` | `String? avatar` |
| Default | `@Default(value)` | `@Default(0) int count` |
| API mapping | `@JsonKey(name: '...')` | `@JsonKey(name: 'created_at')` |
| Custom JSON | `@JsonKey(fromJson: ..., toJson: ...)` | Date parsing |
