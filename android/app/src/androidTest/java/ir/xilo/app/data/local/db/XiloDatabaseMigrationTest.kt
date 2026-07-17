package ir.xilo.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XiloDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(XiloDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesChatAndAddsArchiveState() {
        helper.createDatabase(V1_TO_V2_DB, 1).apply {
            insertV1Chat()
            close()
        }

        helper.runMigrationsAndValidate(
            V1_TO_V2_DB,
            2,
            true,
            XiloMigrations.MIGRATION_1_2
        ).apply {
            query("SELECT lastMessageContent, isArchived FROM chats WHERE id = 'chat-1'").use {
                it.moveToFirst()
                assertEquals("saved", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            close()
        }
    }

    @Test
    fun migrate1To3_runsCompleteNonDestructivePath() {
        helper.createDatabase(V1_TO_V3_DB, 1).apply {
            insertV1Chat()
            close()
        }

        helper.runMigrationsAndValidate(
            V1_TO_V3_DB,
            3,
            true,
            XiloMigrations.MIGRATION_1_2,
            XiloMigrations.MIGRATION_2_3
        ).apply {
            query("SELECT lastMessageContent, isArchived FROM chats WHERE id = 'chat-1'").use {
                it.moveToFirst()
                assertEquals("saved", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            query("SELECT COUNT(*) FROM outbox_operations").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To3_preservesCachedDataAndCreatesOutbox() {
        helper.createDatabase(V2_TO_V3_DB, 2).apply {
            execSQL(
                """
                INSERT INTO chats (
                    id, type, name, avatarUrl, lastMessageContent, lastMessageTime,
                    unreadCount, isMuted, isArchived
                ) VALUES ('chat-1', 'direct', NULL, NULL, 'saved', 100, 2, 0, 0)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            V2_TO_V3_DB,
            3,
            true,
            XiloMigrations.MIGRATION_2_3
        ).apply {
            query("SELECT lastMessageContent, unreadCount FROM chats WHERE id = 'chat-1'").use {
                it.moveToFirst()
                assertEquals("saved", it.getString(0))
                assertEquals(2, it.getInt(1))
            }
            execSQL(
                """
                INSERT INTO outbox_operations (
                    operationKey, operationType, aggregateId, payload, state,
                    attemptCount, createdAt, updatedAt, nextAttemptAt
                ) VALUES (
                    '123e4567-e89b-42d3-a456-426614174000',
                    'message.send', 'chat-1', '{}', 'pending', 0, 1, 1, 1
                )
                """.trimIndent()
            )
            query("SELECT COUNT(*) FROM outbox_operations").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate3To4_preservesServerMessageAsDeliveredAndAddsCorrelation() {
        helper.createDatabase(V3_TO_V4_DB, 3).apply {
            insertV3Message()
            close()
        }

        helper.runMigrationsAndValidate(
            V3_TO_V4_DB,
            4,
            true,
            XiloMigrations.MIGRATION_3_4
        ).apply {
            query(
                """
                SELECT content, clientOperationKey, clientPayloadHash,
                       deliveryState, deliveryErrorCode, deliveryErrorMessage
                FROM messages WHERE id = 'message-1'
                """.trimIndent()
            ).use {
                it.moveToFirst()
                assertEquals("saved message", it.getString(0))
                assertNull(it.getString(1))
                assertNull(it.getString(2))
                assertEquals("delivered", it.getString(3))
                assertNull(it.getString(4))
                assertNull(it.getString(5))
            }
            close()
        }
    }

    @Test
    fun migrate1To4_runsCompleteNonDestructivePath() {
        helper.createDatabase(V1_TO_V4_DB, 1).apply {
            insertV1Chat()
            close()
        }

        helper.runMigrationsAndValidate(
            V1_TO_V4_DB,
            4,
            true,
            *XiloMigrations.ALL
        ).apply {
            query("SELECT lastMessageContent, isArchived FROM chats WHERE id = 'chat-1'").use {
                it.moveToFirst()
                assertEquals("saved", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            query("SELECT COUNT(*) FROM outbox_operations").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To4_preservesCachedMessageAndChat() {
        helper.createDatabase(V2_TO_V4_DB, 2).apply {
            execSQL(
                """
                INSERT INTO chats (
                    id, type, name, avatarUrl, lastMessageContent, lastMessageTime,
                    unreadCount, isMuted, isArchived
                ) VALUES ('chat-1', 'direct', NULL, NULL, 'saved', 100, 2, 0, 0)
                """.trimIndent()
            )
            insertV3Message()
            close()
        }

        helper.runMigrationsAndValidate(
            V2_TO_V4_DB,
            4,
            true,
            XiloMigrations.MIGRATION_2_3,
            XiloMigrations.MIGRATION_3_4
        ).apply {
            query("SELECT deliveryState FROM messages WHERE id = 'message-1'").use {
                it.moveToFirst()
                assertEquals("delivered", it.getString(0))
            }
            query("SELECT unreadCount FROM chats WHERE id = 'chat-1'").use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate4To5_preservesMessagesAndAddsSoftDeleteFlag() {
        helper.createDatabase(V4_TO_V5_DB, 4).apply {
            insertV4Message()
            close()
        }

        helper.runMigrationsAndValidate(
            V4_TO_V5_DB,
            5,
            true,
            XiloMigrations.MIGRATION_4_5
        ).apply {
            query(
                """
                SELECT content, deliveryState, isDeleted
                FROM messages WHERE id = 'message-1'
                """.trimIndent()
            ).use {
                it.moveToFirst()
                assertEquals("saved message", it.getString(0))
                assertEquals("delivered", it.getString(1))
                assertEquals(0, it.getInt(2))
            }
            close()
        }
    }

    @Test
    fun migrate1To5_runsCompleteNonDestructivePath() {
        helper.createDatabase(V1_TO_V5_DB, 1).apply {
            insertV1Chat()
            close()
        }

        helper.runMigrationsAndValidate(
            V1_TO_V5_DB,
            5,
            true,
            *XiloMigrations.ALL
        ).apply {
            query("SELECT lastMessageContent, isArchived FROM chats WHERE id = 'chat-1'").use {
                it.moveToFirst()
                assertEquals("saved", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            query("SELECT COUNT(*) FROM outbox_operations").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate6To7_addsRepostColumnsToPosts() {
        helper.createDatabase(V6_TO_V7_DB, 6).apply {
            execSQL(
                """
                INSERT INTO posts (
                    id, authorId, authorName, authorUsername, authorAvatar,
                    title, slug, content, excerpt, coverImageUrl,
                    likeCount, commentCount, isLiked, isBookmarked, createdAt
                ) VALUES (
                    'post-1', 'author-1', 'Author', 'author', NULL,
                    'Title', 'slug', 'body', NULL, NULL,
                    1, 2, 0, 0, 100
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            V6_TO_V7_DB,
            7,
            true,
            XiloMigrations.MIGRATION_6_7
        ).apply {
            query(
                """
                SELECT likeCount, commentCount, repostCount, isReposted
                FROM posts WHERE id = 'post-1'
                """.trimIndent()
            ).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(2, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
            close()
        }
    }

    @Test
    fun migrate7To8_addsFeedRankToPosts() {
        helper.createDatabase(V7_TO_V8_DB, 7).apply {
            execSQL(
                """
                INSERT INTO posts (
                    id, authorId, authorName, authorUsername, authorAvatar,
                    title, slug, content, excerpt, coverImageUrl,
                    likeCount, commentCount, repostCount, isLiked, isBookmarked,
                    isReposted, createdAt
                ) VALUES (
                    'post-1', 'author-1', 'Author', 'author', NULL,
                    'Title', 'slug', 'body', NULL, NULL,
                    1, 2, 0, 0, 0,
                    0, 100
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            V7_TO_V8_DB,
            8,
            true,
            XiloMigrations.MIGRATION_7_8
        ).apply {
            query(
                """
                SELECT createdAt, feedRank
                FROM posts WHERE id = 'post-1'
                """.trimIndent()
            ).use {
                it.moveToFirst()
                assertEquals(100, it.getLong(0))
                assertEquals(Int.MAX_VALUE, it.getInt(1))
            }
            close()
        }
    }

    @Test
    fun migrate8To9_addsDislikeColumnsToComments() {
        helper.createDatabase(V8_TO_V9_DB, 8).apply {
            execSQL(
                """
                INSERT INTO comments (
                    id, postId, authorId, authorName, authorUsername, authorAvatar,
                    parentId, rootId, depth, content, likeCount, replyCount,
                    isLiked, isPinned, createdAt
                ) VALUES (
                    'comment-1', 'post-1', 'author-1', 'Author', 'author', NULL,
                    NULL, NULL, 0, 'hello', 3, 1,
                    0, 0, 100
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            V8_TO_V9_DB,
            9,
            true,
            XiloMigrations.MIGRATION_8_9
        ).apply {
            query(
                """
                SELECT likeCount, replyCount, dislikeCount, isDisliked
                FROM comments WHERE id = 'comment-1'
                """.trimIndent()
            ).use {
                it.moveToFirst()
                assertEquals(3, it.getInt(0))
                assertEquals(1, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
            close()
        }
    }

    @Test
    fun migrate9To10_addsIsBookmarkedToComments() {
        helper.createDatabase(V9_TO_V10_DB, 9).apply {
            execSQL(
                """
                INSERT INTO comments (
                    id, postId, authorId, authorName, authorUsername, authorAvatar,
                    parentId, rootId, depth, content, likeCount, replyCount,
                    isLiked, isPinned, createdAt, dislikeCount, isDisliked
                ) VALUES (
                    'comment-1', 'post-1', 'author-1', 'Author', 'author', NULL,
                    NULL, NULL, 0, 'hello', 3, 1,
                    0, 0, 100, 0, 0
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            V9_TO_V10_DB,
            10,
            true,
            XiloMigrations.MIGRATION_9_10
        ).apply {
            query(
                """
                SELECT isBookmarked FROM comments WHERE id = 'comment-1'
                """.trimIndent()
            ).use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertV1Chat() {
        execSQL(
            """
            INSERT INTO chats (
                id, type, name, avatarUrl, lastMessageContent, lastMessageTime,
                unreadCount, isMuted
            ) VALUES ('chat-1', 'direct', NULL, NULL, 'saved', 100, 2, 0)
            """.trimIndent()
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertV3Message() {
        execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, senderAvatar, content, mediaUrl,
                replyToId, isEdited, isRead, createdAt
            ) VALUES (
                'message-1', 'chat-1', 'sender-1', NULL, NULL, 'saved message',
                NULL, NULL, 0, 0, 100
            )
            """.trimIndent()
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertV4Message() {
        execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, senderAvatar, content, mediaUrl,
                replyToId, isEdited, isRead, clientOperationKey, clientPayloadHash,
                deliveryState, deliveryErrorCode, deliveryErrorMessage, createdAt
            ) VALUES (
                'message-1', 'chat-1', 'sender-1', NULL, NULL, 'saved message',
                NULL, NULL, 0, 0, NULL, NULL, 'delivered', NULL, NULL, 100
            )
            """.trimIndent()
        )
    }

    private companion object {
        const val V1_TO_V2_DB = "migration-1-2-test"
        const val V1_TO_V3_DB = "migration-1-3-test"
        const val V2_TO_V3_DB = "migration-2-3-test"
        const val V3_TO_V4_DB = "migration-3-4-test"
        const val V1_TO_V4_DB = "migration-1-4-test"
        const val V2_TO_V4_DB = "migration-2-4-test"
        const val V4_TO_V5_DB = "migration-4-5-test"
        const val V1_TO_V5_DB = "migration-1-5-test"
        const val V6_TO_V7_DB = "migration-6-7-test"
        const val V7_TO_V8_DB = "migration-7-8-test"
        const val V8_TO_V9_DB = "migration-8-9-test"
        const val V9_TO_V10_DB = "migration-9-10-test"
    }
}
