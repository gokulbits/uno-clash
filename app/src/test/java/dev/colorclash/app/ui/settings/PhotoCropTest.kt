package dev.colorclash.app.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoCropTest {

    @Test
    fun `a photo claiming to be enormous isn't decoded`() {
        assertTrue(photoFits(160, 160))
        assertFalse(photoFits(30_000, 30_000))
        assertFalse(photoFits(160, 513))
        assertFalse(photoFits(0, 160))
        assertFalse(photoFits(-1, -1))
    }
    @Test
    fun `a fresh photo is cropped to its centre square`() {
        assertEquals(CropBox(100, 0, 200), cropBox(400, 200, 300f, CropView()))
        assertEquals(CropBox(0, 150, 300), cropBox(300, 600, 300f, CropView()))
    }

    @Test
    fun `zooming in takes a smaller square around the same centre`() {
        assertEquals(CropBox(150, 50, 100), cropBox(400, 200, 300f, CropView(zoom = 2f)))
    }

    @Test
    fun `dragging the photo right shows more of its left side`() {
        // At 1.5x per source pixel, dragging 150 viewport pixels moves the crop 100 source pixels.
        assertEquals(CropBox(0, 0, 200), cropBox(400, 200, 300f, CropView(panX = 150f)))
        assertEquals(CropBox(200, 0, 200), cropBox(400, 200, 300f, CropView(panX = -150f)))
    }

    @Test
    fun `the photo can't be dragged or zoomed out past its edges`() {
        val far = CropView(zoom = 0.2f, panX = 10_000f, panY = -10_000f).clamped(400, 200, 300f)
        assertEquals(1f, far.zoom)
        assertEquals(150f, far.panX)
        assertEquals(0f, far.panY, absoluteTolerance = 0f)
        assertEquals(CropBox(0, 0, 200), cropBox(400, 200, 300f, CropView(panX = 10_000f, panY = 10_000f)))
        assertEquals(MAX_ZOOM, CropView(zoom = 50f).clamped(400, 200, 300f).zoom)
    }
}
