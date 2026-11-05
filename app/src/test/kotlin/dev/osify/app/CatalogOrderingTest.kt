package dev.osify.app

import dev.osify.app.ui.CatalogOrder
import dev.osify.app.ui.orderCatalog
import dev.osify.core.*
import java.util.Locale
import org.junit.Test
import org.junit.Assert.*

class CatalogOrderingTest {
    private fun image(name: String,bytes: Long)=CatalogImage(name,name,"1",Architecture.X86_64,"test","https://example.org/$name.iso",bytes,"")
    @Test fun namesAndCuratedOrder() {
        val items=listOf(image("Windows",3),image("alpine",1),image("Ubuntu",2))
        assertEquals(items,orderCatalog(items,CatalogOrder.CURATED,Locale.ENGLISH))
        assertEquals(listOf("alpine","Ubuntu","Windows"),orderCatalog(items,CatalogOrder.NAME_ASC,Locale.ENGLISH).map {it.name})
        assertEquals(listOf("Windows","Ubuntu","alpine"),orderCatalog(items,CatalogOrder.NAME_DESC,Locale.ENGLISH).map {it.name})
    }
    @Test fun unknownSizesStayLastAndTiesStayStable() {
        val items=listOf(image("Unknown",0),image("Large",100),image("Small",10),image("Tie",10),image("Missing",-1))
        assertEquals(listOf("Small","Tie","Large","Unknown","Missing"),orderCatalog(items,CatalogOrder.SIZE_ASC,Locale.ENGLISH).map {it.name})
        assertEquals(listOf("Large","Small","Tie","Unknown","Missing"),orderCatalog(items,CatalogOrder.SIZE_DESC,Locale.ENGLISH).map {it.name})
    }
}
