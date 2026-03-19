# AppLogger — Matriz de Compatibilidad

**Versión SDK:** 0.1.1-alpha.2  
**Fecha:** 2026-03-17

Documento de referencia rápida para compatibilidad mínima de plataforma y runtime.

---

## 1. Resumen

| Plataforma | Mínimo soportado | Recomendado | Runtime |
|---|---|---|---|
| Android Mobile | API 23 (Android 6.0) | API 26+ | ART |
| Android TV | API 23 (Android 6.0 TV) | API 28+ | ART |
| iOS | iOS 14 | iOS 16+ | Kotlin/Native + Darwin |
| JVM | JDK 11 | JDK 17 | HotSpot / OpenJDK |

---

## 2. Política

- El mínimo se revisa en cada release mayor o cuando la cuota de mercado cambia significativamente.
- Se prioriza mantener cobertura de mercado sin comprometer estabilidad de runtime.
- Las funcionalidades nuevas pueden requerir versión recomendada superior al mínimo.

---

## 3. Notas por plataforma

### Android

- `minSdk = 23`
- `targetSdk` debe seguir la política vigente de Google Play
- ADB capture solo en builds de debug

### iOS

- Distribución via XCFramework
- Build principal via Kotlin Multiplatform (target iOS)
- Integración recomendada: KMP puro (`commonMain` + `iosMain`) sin capa host externa

### JVM

- Uso recomendado para tooling interno, pruebas e ingestores
