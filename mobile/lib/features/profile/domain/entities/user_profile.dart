class ProfileStats {
  final int posts;
  final int followers;
  final int following;

  const ProfileStats({
    this.posts = 0,
    this.followers = 0,
    this.following = 0,
  });

  factory ProfileStats.fromJson(Map<String, dynamic> json) {
    return ProfileStats(
      posts: (json['posts'] as num?)?.toInt() ?? 0,
      followers: (json['followers'] as num?)?.toInt() ?? 0,
      following: (json['following'] as num?)?.toInt() ?? 0,
    );
  }
}

class UserProfile {
  final String id;
  final String username;
  final String displayName;
  final String avatarUrl;
  final String bio;
  final bool isVerified;
  final DateTime createdAt;
  final ProfileStats stats;
  final bool isFollowing;

  const UserProfile({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarUrl = '',
    this.bio = '',
    this.isVerified = false,
    required this.createdAt,
    required this.stats,
    this.isFollowing = false,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      id: json['id'] as String,
      username: json['username'] as String,
      displayName: json['display_name'] as String? ?? json['username'] as String,
      avatarUrl: json['avatar_url'] as String? ?? '',
      bio: json['bio'] as String? ?? '',
      isVerified: json['is_verified'] as bool? ?? false,
      createdAt: DateTime.parse(json['created_at'] as String),
      stats: ProfileStats.fromJson(json['stats'] as Map<String, dynamic>? ?? {}),
      isFollowing: json['is_following'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'username': username,
        'display_name': displayName,
        'avatar_url': avatarUrl,
        'bio': bio,
        'is_verified': isVerified,
        'created_at': createdAt.toIso8601String(),
        'stats': {
          'posts': stats.posts,
          'followers': stats.followers,
          'following': stats.following,
        },
        'is_following': isFollowing,
      };
}
