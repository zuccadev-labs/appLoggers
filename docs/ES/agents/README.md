# Agents — Paquetes de Agent Skills para AppLogger

Este directorio contiene **paquetes de skills portables** para que otra persona los copie manualmente a su propio proyecto Kotlin o Kotlin Multiplatform.

Estos skills **no deben estar activos dentro de este repositorio del SDK**. Por eso ya no viven en `.github/skills/` de este repo.

Cada carpeta sigue el formato estándar de Agent Skills:

1. `SKILL.md`
2. `references/`

## Skills disponibles

| Skill | Objetivo | Carpeta |
| --- | --- | --- |
| Guided setup | Guiar paso a paso la instalación y configuración del SDK | [applogger-guided-setup](applogger-guided-setup) |
| Project integration | Leer una app existente, decidir puntos de integración y cablear AppLogger correctamente | [applogger-project-integration](applogger-project-integration) |
| Runtime troubleshooting | Diagnosticar por qué no llegan eventos o falla el transporte | [applogger-runtime-troubleshooting](applogger-runtime-troubleshooting) |
| Production hardening | Endurecer seguridad, privacidad y parametros para release | [applogger-production-hardening](applogger-production-hardening) |
| Instrumentation design | Definir estrategia de eventos, tags y metricas de alto valor | [applogger-instrumentation-design](applogger-instrumentation-design) |
| Integration validation | Ejecutar smoke checks y criterios de aceptacion de la integracion | [applogger-integration-validation](applogger-integration-validation) |

## Cómo usar estos paquetes en otro proyecto

1. Copia la carpeta del skill deseado al repo destino.
2. Pégala en `.github/skills/<nombre-del-skill>/`.
3. Conserva `SKILL.md` y la carpeta `references/`.
4. En el chat del agente, pide la tarea usando frases afines al `description` del skill.

## Cuándo usar cada skill

### Guided setup

Usa este skill cuando la persona quiera una guía paso a paso, por ejemplo:

1. "Guíame para configurar el SDK"
2. "Ayúdame a instalar AppLogger"
3. "Configura el logger en mi proyecto KMP"

### Project integration

Usa este skill cuando la persona quiera que el agente analice la app e integre AppLogger en los puntos correctos, por ejemplo:

1. "Lee la app e integra el SDK"
2. "Analiza el proyecto y dime dónde inicializar el logger"
3. "Revisa mi arquitectura y agrega AppLogger de forma segura"

### Runtime troubleshooting

Usa este skill cuando la integracion existe pero algo falla, por ejemplo:

1. "No llegan eventos"
2. "Revisa por que transportAvailable sale false"
3. "El buffer sube y no baja"

### Production hardening

Usa este skill para preparar la configuracion de produccion, por ejemplo:

1. "Haz hardening del SDK"
2. "Revisa seguridad y PII"
3. "Ajusta parametros para release"

### Instrumentation design

Usa este skill para disenar que loguear y como modelarlo, por ejemplo:

1. "Disena la estrategia de logging"
2. "Define tags y eventos"
3. "Que metricas debo instrumentar"

### Integration validation

Usa este skill para validar si la integracion ya esta lista, por ejemplo:

1. "Haz smoke test de AppLogger"
2. "Valida la integracion"
3. "Dame checklist de QA"

## Alcance

Los ejemplos y flujos estan alineados con AppLogger `v0.1.1-alpha.1` y con la politica actual de iOS KMP-only.

## Regla clave de configuracion

En cualquier skill que toque configuracion:

1. Verificar si `local.properties` existe.
2. Verificar si las claves de AppLogger existen.
3. Si faltan claves, agregarlas directamente.
4. No modificar, renombrar ni borrar otras variables existentes.
