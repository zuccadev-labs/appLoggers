# Klinema Patterns

## Patterns worth keeping

1. Bootstrap centralizado en `Application` mediante un inicializador dedicado.
2. Taxonomía de tags fija por dominios operativos.
3. Captura de identidad inicial por fingerprint seudónimo y actualización posterior desde auth.
4. Métricas operativas por latencia y conteo en auth, gRPC, servicios y filtros.
5. Snapshot de salud al apagar y `flush()` antes de terminar el servicio.

## Patterns to improve if repeated elsewhere

1. No registrar el `userId` autenticado dentro del mensaje si ya se usa para correlación del SDK.
2. No depender de mensajes con emojis o demasiado narrativos cuando el equipo operativo necesita búsquedas exactas.
3. No duplicar endpoint y anon key en varios archivos de configuración salvo necesidad real.
4. No dejar versiones históricas del SDK si el repositorio fuente ya expone una versión más nueva y compatible.

## Upgrade decision model

1. Keep if the pattern improves observability and does not leak data.
2. Document if the pattern is good but non-obvious for future agents.
3. Correct if the pattern leaks identifiers, harms operability, or drifts from the current schema contract.