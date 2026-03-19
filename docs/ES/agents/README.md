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

## Alcance

Los ejemplos y flujos están alineados con AppLogger `v0.1.1-alpha.1` y con la política actual de iOS KMP-only.
