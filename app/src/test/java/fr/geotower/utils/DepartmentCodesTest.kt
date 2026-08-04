package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le découpage doit rester identique à `department_code()` de docs/server/fr_dept_stats.py :
 * c'est lui qui a produit les clés de `dept_stat_current`.
 */
class DepartmentCodesTest {

    @Test
    fun metropolitanCodesKeepTheirTwoFirstDigits() {
        assertEquals("72", DepartmentCodes.fromInsee("72181"))
        assertEquals("01", DepartmentCodes.fromInsee("01004"))
        assertEquals("75", DepartmentCodes.fromInsee("75056"))
    }

    @Test
    fun corsicaKeepsItsLetter() {
        assertEquals("2A", DepartmentCodes.fromInsee("2A004"))
        assertEquals("2B", DepartmentCodes.fromInsee("2b033"))
    }

    @Test
    fun overseasCodesUseThreeDigits() {
        assertEquals("974", DepartmentCodes.fromInsee("97411"))
        assertEquals("987", DepartmentCodes.fromInsee("98735"))
        assertEquals("988", DepartmentCodes.fromInsee("98818"))
    }

    @Test
    fun leadingZeroIsRestored() {
        assertEquals("01", DepartmentCodes.fromInsee("1004"))
    }

    @Test
    fun unusableCodesReturnNull() {
        assertNull(DepartmentCodes.fromInsee(null))
        assertNull(DepartmentCodes.fromInsee(""))
        assertNull(DepartmentCodes.fromInsee("   "))
        assertNull(DepartmentCodes.fromInsee("72"))
        assertNull(DepartmentCodes.fromInsee("ABCDE"))
    }
}
