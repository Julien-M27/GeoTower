package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les zones résolues ici pilotent le cadrage ET le filtre SQL de la carte : un code faux n'affiche
 * pas « à peu près » le bon département, il affiche celui d'à côté.
 */
class FrenchAdminAreasTest {

    @Test
    fun bareDepartmentCodesResolveToDepartments() {
        val illeEtVilaine = FrenchAdminAreas.match("35")
        assertEquals(FrenchAdminAreas.Kind.DEPARTMENT, illeEtVilaine?.kind)
        assertEquals("35", illeEtVilaine?.code)
        assertEquals("Ille-et-Vilaine", illeEtVilaine?.name)
        assertEquals(listOf("35"), illeEtVilaine?.departmentCodes)

        assertEquals("2A", FrenchAdminAreas.match("2a")?.code)
        assertEquals("974", FrenchAdminAreas.match("974")?.code)
        assertEquals("05", FrenchAdminAreas.match("5")?.code)
    }

    /**
     * Les codes région (84 Auvergne-Rhône-Alpes, 93 PACA…) entrent en collision avec des codes
     * département : une saisie nue doit toujours donner le département.
     */
    @Test
    fun bareTwoDigitCodesNeverResolveToRegions() {
        assertEquals("Vaucluse", FrenchAdminAreas.match("84")?.name)
        assertEquals("Seine-Saint-Denis", FrenchAdminAreas.match("93")?.name)
        assertEquals("Bretagne", FrenchAdminAreas.match("region:53")?.name)
    }

    @Test
    fun namesIgnoreCaseAccentsAndSeparators() {
        assertEquals("35", FrenchAdminAreas.match("ille-et-vilaine")?.code)
        assertEquals("35", FrenchAdminAreas.match("ILLE ET VILAINE")?.code)
        assertEquals("22", FrenchAdminAreas.match("cotes d'armor")?.code)
        assertEquals("29", FrenchAdminAreas.match("finistere")?.code)
        assertEquals("11", FrenchAdminAreas.match("ile de france")?.code)
        assertEquals("93", FrenchAdminAreas.match("paca")?.code)
    }

    @Test
    fun regionsCarryTheirDepartments() {
        val bretagne = FrenchAdminAreas.match("Bretagne")
        assertEquals(FrenchAdminAreas.Kind.REGION, bretagne?.kind)
        assertEquals(listOf("22", "29", "35", "56"), bretagne?.departmentCodes?.sorted())

        val corse = FrenchAdminAreas.match("Corse")
        assertEquals(listOf("2A", "2B"), corse?.departmentCodes)

        // Toutes les régions doivent porter au moins un département, sinon la carte n'aurait
        // aucune emprise à calculer.
        listOf("Auvergne-Rhône-Alpes", "Occitanie", "Grand Est", "Normandie", "Mayotte").forEach { name ->
            val region = FrenchAdminAreas.match(name)
            assertNotNull(name, region)
            assertTrue(name, region!!.departmentCodes.isNotEmpty())
        }
    }

    @Test
    fun prefixesSelectTheKind() {
        assertEquals("29", FrenchAdminAreas.match("dept:29")?.code)
        assertEquals("29", FrenchAdminAreas.match("département : Finistère")?.code)
        assertEquals("76", FrenchAdminAreas.match("region:Occitanie")?.code)
        assertEquals(FrenchAdminAreas.Kind.REGION, FrenchAdminAreas.match("r:11")?.kind)
        assertNull(FrenchAdminAreas.match("dept:Bretagne"))
    }

    @Test
    fun unrelatedQueriesAreNotAreas() {
        assertNull(FrenchAdminAreas.match(""))
        assertNull(FrenchAdminAreas.match("   "))
        assertNull(FrenchAdminAreas.match("Rennes"))
        assertNull(FrenchAdminAreas.match("0350123"))
        assertNull(FrenchAdminAreas.match("96"))
        assertNull(FrenchAdminAreas.match("20"))
        assertNull(FrenchAdminAreas.match("op:orange"))
    }

    /**
     * Les bornes servent telles quelles au `WHERE code_insee >= :start AND code_insee < :end` :
     * la borne haute doit exclure le département suivant, y compris en Corse et outre-mer.
     */
    @Test
    fun inseeRangesCoverExactlyTheirDepartment() {
        assertEquals("35" to "36", FrenchAdminAreas.inseeRange("35"))
        assertEquals("09" to "0:", FrenchAdminAreas.inseeRange("09"))
        assertEquals("2A" to "2B", FrenchAdminAreas.inseeRange("2A"))
        assertEquals("2B" to "2C", FrenchAdminAreas.inseeRange("2B"))
        assertEquals("974" to "975", FrenchAdminAreas.inseeRange("974"))

        // Un code INSEE corse du sud doit tomber dans 2A et pas dans 2B.
        val (start2A, end2A) = FrenchAdminAreas.inseeRange("2A")
        assertTrue("2A004" >= start2A && "2A004" < end2A)
        assertTrue("2B033" >= end2A)

        // 09xxx ne doit pas déborder sur 10xxx.
        val (start09, end09) = FrenchAdminAreas.inseeRange("09")
        assertTrue("09122" >= start09 && "09122" < end09)
        assertTrue("10001" >= end09)
    }

    @Test
    fun departmentNamesAreExposedForDisplay() {
        assertEquals("Nord", FrenchAdminAreas.departmentName("59"))
        assertEquals("Haute-Corse", FrenchAdminAreas.departmentName("2B"))
        assertNull(FrenchAdminAreas.departmentName("96"))
    }
}
