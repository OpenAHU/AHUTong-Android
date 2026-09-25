package com.ahu.ahutong.data.crawler.gmis

import org.junit.Assert.assertEquals
import org.junit.Test

class GmisLocationFormatterTest {
    @Test fun shortensObservedBuildingsWithoutDiscardingRoomNumbers() {
        assertEquals("主楼101", GmisLocationFormatter.display("（江淮）教学主楼101"))
        assertEquals("主楼北阶202", GmisLocationFormatter.display("(江淮)教学主楼北阶202"))
        assertEquals("主楼二阶305", GmisLocationFormatter.display("（江淮）教学主楼二楼阶梯305"))
    }

    @Test fun choosesTheLongestBuildingNameBeforeItsSharedPrefix() {
        assertEquals("主楼北阶101", GmisLocationFormatter.display("教学主楼北阶101"))
        assertEquals("主楼二阶101", GmisLocationFormatter.display("教学主楼二楼阶梯101"))
    }

    @Test fun keepsUnrecognizedBuildingsAndOtherCampusLabelsReadable() {
        assertEquals("实验楼101", GmisLocationFormatter.display("（江淮）实验楼101"))
        assertEquals("（其他校区）实验楼101", GmisLocationFormatter.display("（其他校区）实验楼101"))
    }
}
