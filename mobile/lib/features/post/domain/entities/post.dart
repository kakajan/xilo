class Post {
  final String id;
  final String authorId;
  final String title;
  final String slug;
  final String excerpt;
  final String content;
  final String contentMd;
  final String? coverImageUrl;
  final String? category;
  final List<String> tags;
  final String status;
  final bool isPremium;
  final String? language;
  final int? wordCount;
  final int? readingTime;
  final DateTime? publishedAt;
  final DateTime createdAt;
  final Author? author;

  Post({
    required this.id,
    required this.authorId,
    required this.title,
    required this.slug,
    required this.excerpt,
    required this.content,
    this.contentMd = '',
    this.coverImageUrl,
    this.category,
    this.tags = const [],
    required this.status,
    this.isPremium = false,
    this.language,
    this.wordCount,
    this.readingTime,
    this.publishedAt,
    required this.createdAt,
    this.author,
  });

  factory Post.fromJson(Map<String, dynamic> json) {
    return Post(
      id: json['id'] as String,
      authorId: json['author_id'] as String,
      title: json['title'] as String,
      slug: json['slug'] as String,
      excerpt: json['excerpt'] as String? ?? '',
      content: json['content'] as String? ?? '',
      contentMd: json['content_md'] as String? ?? '',
      coverImageUrl: json['cover_image_url'] as String?,
      category: json['category'] as String?,
      tags: (json['tags'] as List<dynamic>?)?.map((e) => e as String).toList() ?? [],
      status: json['status'] as String? ?? 'draft',
      isPremium: json['is_premium'] as bool? ?? false,
      language: json['language'] as String?,
      wordCount: (json['word_count'] as num?)?.toInt(),
      readingTime: (json['reading_time'] as num?)?.toInt(),
      publishedAt: json['published_at'] != null ? DateTime.parse(json['published_at'] as String) : null,
      createdAt: DateTime.parse(json['created_at'] as String),
      author: json['author'] != null ? Author.fromJson(json['author'] as Map<String, dynamic>) : null,
    );
  }
}

class Author {
  final String id;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final String? bio;

  Author({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarUrl,
    this.bio,
  });

  factory Author.fromJson(Map<String, dynamic> json) {
    return Author(
      id: json['id'] as String,
      username: json['username'] as String,
      displayName: json['display_name'] as String,
      avatarUrl: json['avatar_url'] as String?,
      bio: json['bio'] as String?,
    );
  }
}
