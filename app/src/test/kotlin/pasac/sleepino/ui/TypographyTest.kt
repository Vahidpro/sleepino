package pasac.sleepino.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TypographyTest {
 @Test fun allPersianTypographyStylesUseConfiguredFontFamily() {
  val typography = appTypography(persian = true)
  val expectedFontFamily = typography.titleLarge.fontFamily
  assertNotNull(expectedFontFamily)

  val styles = listOf(
   typography.displayLarge,
   typography.displayMedium,
   typography.displaySmall,
   typography.headlineLarge,
   typography.headlineMedium,
   typography.headlineSmall,
   typography.titleLarge,
   typography.titleMedium,
   typography.titleSmall,
   typography.bodyLarge,
   typography.bodyMedium,
   typography.bodySmall,
   typography.labelLarge,
   typography.labelMedium,
   typography.labelSmall
  )

  for (style in styles) {
   assertEquals(expectedFontFamily, style.fontFamily)
  }
  assertEquals(FontWeight.SemiBold, typography.headlineSmall.fontWeight)
 }

 @Test fun englishTypographyStylesUseSansSerifFontFamily() {
  val typography = appTypography(persian = false)
  assertEquals(FontFamily.SansSerif, typography.headlineSmall.fontFamily)
  assertEquals(FontFamily.SansSerif, typography.displaySmall.fontFamily)
 }
}
