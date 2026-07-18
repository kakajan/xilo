package ir.xilo.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object XiloMigrations {
    /**
     * Git history proves v1 and v2 differ only by ChatEntity.isArchived.
     * Existing rows remain active (not archived).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE chats ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS outbox_operations (
                    operationKey TEXT NOT NULL,
                    operationType TEXT NOT NULL,
                    aggregateId TEXT,
                    payload TEXT NOT NULL,
                    state TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    nextAttemptAt INTEGER NOT NULL,
                    inFlightAt INTEGER,
                    errorCode TEXT,
                    errorHttpStatus INTEGER,
                    errorMessage TEXT,
                    PRIMARY KEY(operationKey)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_outbox_operations_state_nextAttemptAt_createdAt
                ON outbox_operations(state, nextAttemptAt, createdAt)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_outbox_operations_aggregateId_createdAt
                ON outbox_operations(aggregateId, createdAt)
                """.trimIndent()
            )
        }
    }

    /**
     * Adds durable optimistic-message correlation and delivery state. Cached
     * server messages predate the outbox echo and are authoritative/delivered.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN clientOperationKey TEXT"
            )
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN clientPayloadHash TEXT"
            )
            db.execSQL(
                """
                ALTER TABLE messages
                ADD COLUMN deliveryState TEXT NOT NULL DEFAULT 'delivered'
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN deliveryErrorCode TEXT"
            )
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN deliveryErrorMessage TEXT"
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                index_messages_clientOperationKey
                ON messages(clientOperationKey)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_messages_chatId_createdAt
                ON messages(chatId, createdAt)
                """.trimIndent()
            )
        }
    }

    /**
     * Soft-delete for realtime `message.delete`. Existing rows remain visible
     * until a delete event arrives; defaults keep history non-destructive.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE messages
                ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN phone TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_folders (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_chat_folders_sortOrder
                ON chat_folders(sortOrder)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_folder_items (
                    folderId TEXT NOT NULL,
                    chatId TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    PRIMARY KEY(folderId, chatId)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_chat_folder_items_chatId
                ON chat_folder_items(chatId)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN repostCount INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN isReposted INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * Freeze feed order across like/repost updates. Rank is assigned on refresh only.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE posts ADD COLUMN feedRank INTEGER NOT NULL DEFAULT 2147483647"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_posts_feedRank ON posts(feedRank)"
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE comments ADD COLUMN dislikeCount INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE comments ADD COLUMN isDisliked INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE comments ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * Persist peer identity for direct/group chats so ContactDetail can show
     * real username/display name without inventing placeholders.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chats ADD COLUMN peerUserId TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN peerUsername TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN peerDisplayName TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN peerAvatarUrl TEXT")
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11
    )
}
