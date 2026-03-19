# Sample — Uso del SDK AppLogger

Ejemplo mínimo que demuestra cómo importar y usar AppLogger en una app Android.

## Estructura

```
sample/
├── build.gradle.kts          ← Configuración del módulo de ejemplo
└── src/main/kotlin/
    └── com/example/sample/
        ├── SampleApplication.kt    ← Inicialización del SDK
        ├── SampleUsage.kt          ← Ejemplos de uso de cada API
        └── SampleViewModel.kt      ← Inyección de logger en ViewModel
```

## Cómo usar

1. Asegúrate de tener el SDK publicado (o usar `includeBuild` / `composite build`)
2. Configura tus credenciales en `local.properties`
3. Ejecuta los ejemplos

## Nota

Este módulo es solo de referencia, pero sí forma parte de la validación del CI del SDK mediante `:sample:testDebugUnitTest`.
Para compilarlo localmente, asegúrate de tener Android SDK configurado en `sdk/local.properties`.
