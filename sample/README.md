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

Este módulo es solo de referencia. No se compila como parte del CI del SDK.
Para compilarlo, añade `include(":sample")` a `settings.gradle.kts`.
