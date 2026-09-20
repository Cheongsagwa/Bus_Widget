# Main navigation assets

Source: Figma file `XAdrdIktOF8zBrbr3o78X9`.
Frames: main `41:703`, favorites `67:1193`, expanded favorites `67:2515`, menu `67:3351`.
Downloaded on 2026-09-20. SVG files are the original Figma asset bytes.

`ConvertIcons.java` uses Android SDK's `Svg2Vector` importer to generate
`app/src/main/res/drawable/ic_nav_*.xml` without redrawing the glyphs.
Run using Java 21 with sdk-common/common 31.13.2, Guava and Kotlin stdlib on the classpath.
Arguments: this source directory, then the Android drawable directory.

Figma supplies the two endpoint layouts, not animation keyframes. Favorites uses a new
460ms Compose morph; height, margins, corners, blur and tint share the same progress.
Compose's system animation-duration scale applies to the transition.
