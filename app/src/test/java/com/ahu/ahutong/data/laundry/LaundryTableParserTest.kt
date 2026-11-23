package com.ahu.ahutong.data.laundry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 线上台账 table.json 解析测试（openahu.org/download/laundry/table.json）。
 * 样本为真实结构节选（2026-11-23，version 1，809 台全表抽取片段）。
 */
class LaundryTableParserTest {

    private val realSample = """
        {
          "version": 1,
          "updatedAt": "2026-11-23",
          "scannedAt": 1795444909,
          "count": 809,
          "noteEnum": ["女生专用", "男生专用", "维修中", "已停用"],
          "query": {"orderUrl": "https://m.leyaoyao.com/gw/washing/customer/order/get-order-list-home-page?equipmentValue=", "cacheTtlSec": 30},
          "parks": [
            {
              "parkId": 6,
              "parkName": "榴园",
              "shortName": "榴",
              "deviceCounts": {"dryer": 13, "washingMachine": 27, "shoeWasher": 1},
              "devices": [
                {"deviceId": "77546328", "parkId": 6, "deviceType": "dryer", "location": null, "floor": null, "slot": "1", "remark": null, "online": true},
                {"deviceId": "82459496", "parkId": 6, "deviceType": "shoeWasher", "location": null, "floor": null, "slot": null, "remark": null, "online": true},
                {"deviceId": "82459649", "parkId": 6, "deviceType": "washingMachine", "location": null, "floor": null, "slot": "15", "remark": "女生专用", "online": false}
              ]
            },
            {
              "parkId": 13,
              "parkName": "11号楼",
              "shortName": "11号",
              "deviceCounts": {"dryer": 64, "washingMachine": 64, "shoeWasher": 4},
              "devices": [
                {"deviceId": "77546254", "parkId": 13, "deviceType": "dryer", "location": "A1", "floor": null, "slot": "1", "remark": null, "online": true},
                {"deviceId": "77546253", "parkId": 13, "deviceType": "dryer", "location": "A3", "floor": null, "slot": "2", "remark": null, "online": true},
                {"deviceId": "82491350", "parkId": 14, "deviceType": "washingMachine", "location": null, "floor": 1, "slot": "01", "remark": null, "online": true}
              ]
            },
            {
              "parkId": 14,
              "parkName": "南7号楼",
              "shortName": "南7",
              "deviceCounts": {"dryer": 12, "washingMachine": 12, "shoeWasher": 1},
              "devices": [
                {"deviceId": "77546196", "parkId": 14, "deviceType": "dryer", "location": null, "floor": 1, "slot": "1", "remark": null, "online": true},
                {"deviceId": "82491352", "parkId": 14, "deviceType": "washingMachine", "location": null, "floor": 3, "slot": "1", "remark": null, "online": true}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses real sample with parks and devices`() {
        val table = LaundryTableParser.parse(realSample)
        assertEquals(1, table.version)
        assertEquals("2026-11-23", table.updatedAt)
        assertEquals(listOf("榴园", "11号楼", "南7号楼"), table.parks)
        assertEquals(8, table.devices.size)
    }

    @Test
    fun `device fields carry through kind slot location floor remark`() {
        val table = LaundryTableParser.parse(realSample)
        val washer15 = table.devices.first { it.id == "82459649" }
        assertEquals(LaundryKind.WASHER, washer15.kind)
        assertEquals(6, washer15.parkIndex)
        assertEquals("15", washer15.slot)
        assertEquals("女生专用", washer15.remark)
        assertTrue(!washer15.online)

        val dryerA1 = table.devices.first { it.id == "77546254" }
        assertEquals(LaundryKind.DRYER, dryerA1.kind)
        assertEquals("A1", dryerA1.location)
        assertNull(dryerA1.floor)

        val floor1 = table.devices.first { it.id == "82491352" }
        assertEquals(3, floor1.floor)
    }

    @Test
    fun `shoe washer has null slot`() {
        val table = LaundryTableParser.parse(realSample)
        val shoe = table.devices.first { it.kind == LaundryKind.SHOE }
        assertNull(shoe.slot)
    }

    @Test
    fun `device inherits park id not array position`() {
        // 解析器以园区对象的 parkId 为权威（真实数据 device.parkId 与所属园区恒一致），
        // 数组里混入异 parkId 的设备也归到所属园区下。
        val table = LaundryTableParser.parse(realSample)
        val stray = table.devices.first { it.id == "82491350" }
        assertEquals(13, stray.parkIndex)
    }

    @Test
    fun `unknown device type is skipped`() {
        val json = """
            {"version": 2, "updatedAt": "x", "parks": [
              {"parkId": 0, "parkName": "梅园", "devices": [
                {"deviceId": "1", "parkId": 0, "deviceType": "blower", "slot": "1", "online": true},
                {"deviceId": "2", "parkId": 0, "deviceType": "washingMachine", "slot": "2", "online": true}
              ]}
            ]}
        """.trimIndent()
        val table = LaundryTableParser.parse(json)
        assertEquals(1, table.devices.size)
        assertEquals("2", table.devices[0].id)
    }

    @Test
    fun `missing fields fall back safely`() {
        val json = """
            {"version": 5, "parks": [
              {"parkName": "梅园", "devices": [
                {"deviceId": "9", "deviceType": "dryer"}
              ]}
            ]}
        """.trimIndent()
        val table = LaundryTableParser.parse(json)
        assertEquals(5, table.version)
        assertEquals("", table.updatedAt)
        assertEquals(listOf("梅园"), table.parks)
        val device = table.devices[0]
        assertEquals(0, device.parkIndex) // parkId 缺失回落数组下标
        assertNull(device.slot)
        assertEquals(false, device.online)
    }

    @Test
    fun `bad json returns null but missing parks degrades to empty table`() {
        assertNull(LaundryTableParser.parseOrNull("not json at all"))
        // parks 缺失不炸：降级为空表（isEmpty 兜底逻辑会接管）
        val bare = LaundryTableParser.parseOrNull("""{"version": 1}""")
        org.junit.Assert.assertNotNull(bare)
        assertTrue(bare!!.isEmpty)
    }

    @Test
    fun `empty parks yields empty table`() {
        val table = LaundryTableParser.parse("""{"version": 3, "parks": []}""")
        assertTrue(table.isEmpty)
        assertNotNull(table)
    }
}
