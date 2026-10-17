package com.github.toober77.fastpaste

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * JVM check of the v1 -> v2 migration SQL against Room's exported schemas, using sqlite-jdbc.
 * (Room's MigrationTestHelper needs a device/emulator; see androidTest/MigrationTest for that.)
 */
class MigrationSqlTest {
    private lateinit var conn: Connection

    private fun schema(version: Int): JsonObject {
        val file = File("schemas/com.github.toober77.fastpaste.AppDatabase/$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun entity(version: Int) =
        schema(version)["entities"]!!.jsonArray.single().jsonObject

    private data class Col(val name: String, val type: String, val notNull: Boolean, val default: String?)

    private fun actualColumns(): Set<Col> = conn.createStatement().use { st ->
        st.executeQuery("PRAGMA table_info(`paste_items`)").use { rs ->
            buildSet {
                while (rs.next()) {
                    add(Col(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value")))
                }
            }
        }
    }

    private fun expectedColumns(version: Int): Set<Col> =
        entity(version)["fields"]!!.jsonArray.map { it.jsonObject }.mapTo(HashSet()) {
            Col(
                name = it["columnName"]!!.jsonPrimitive.content,
                type = it["affinity"]!!.jsonPrimitive.content,
                notNull = it["notNull"]!!.jsonPrimitive.boolean,
                default = it["defaultValue"]?.jsonPrimitive?.content
            )
        }

    private fun exec(sql: String) = conn.createStatement().use { it.execute(sql) }

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val createV1 = entity(1)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", "paste_items")
        exec(createV1)
        exec("INSERT INTO paste_items (id, content, label, createdAt) VALUES (1, 'hello', 'work', 111)")
        exec("INSERT INTO paste_items (id, content, label, createdAt) VALUES (2, '世界', '', 222)")
    }

    @After
    fun tearDown() = conn.close()

    @Test
    fun schemaFiles_haveExpectedVersions() {
        assertEquals("1", schema(1)["version"]!!.jsonPrimitive.content)
        assertEquals("2", schema(2)["version"]!!.jsonPrimitive.content)
        assertEquals("3", schema(3)["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun migration1to2_producesExactV2Columns() {
        assertEquals(expectedColumns(1), actualColumns())
        Migrations.MIGRATION_1_2_SQL.forEach(::exec)
        assertEquals(expectedColumns(2), actualColumns())
    }

    @Test
    fun migration1to2_keepsRows_andFillsDefaults() {
        Migrations.MIGRATION_1_2_SQL.forEach(::exec)
        val rows = conn.createStatement().use { st ->
            st.executeQuery("SELECT id, content, label, createdAt, pinned, favorite, updatedAt FROM paste_items ORDER BY id").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(listOf(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getInt(5), rs.getInt(6), rs.getLong(7)))
                    }
                }
            }
        }
        assertEquals(
            listOf(
                listOf(1, "hello", "work", 111L, 0, 0, 111L),
                listOf(2, "世界", "", 222L, 0, 0, 222L)
            ),
            rows
        )
    }

    @Test
    fun migration2to3_producesExactV3Columns() {
        Migrations.MIGRATION_1_2_SQL.forEach(::exec)
        Migrations.MIGRATION_2_3_SQL.forEach(::exec)
        assertEquals(expectedColumns(3), actualColumns())
    }

    @Test
    fun migration2to3_keepsRows_andRanksNewestFirst() {
        Migrations.MIGRATION_1_2_SQL.forEach(::exec)
        exec("INSERT INTO paste_items (id, content, label, createdAt) VALUES (3, 'tie', '', 222)")
        Migrations.MIGRATION_2_3_SQL.forEach(::exec)
        val rows = conn.createStatement().use { st ->
            st.executeQuery("SELECT id, content, position FROM paste_items ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getInt(1), rs.getString(2), rs.getInt(3))) }
            }
        }
        // createdAt 222 tie broken by higher id first.
        assertEquals(listOf(Triple(1, "hello", 2), Triple(2, "世界", 1), Triple(3, "tie", 0)), rows)
    }
}
