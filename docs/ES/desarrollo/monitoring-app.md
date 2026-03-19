# AppLogger — App de Monitoreo Externo

**Versión:** 0.1.1-alpha.3  
**Fecha:** 2026-03-17  
**Tipo:** Aplicación externa (no parte del SDK)

Documentación para la aplicación externa que consume los datos almacenados por el SDK AppLogger.

---

## 1. Arquitectura

```
App Cliente (SDK) ──envía──▶ Supabase ──lee──▶ App de Monitoreo
```

El SDK AppLogger solo escribe datos. La app de monitoreo es una aplicación separada que solo lee.

**Principios:**
- Separación completa de responsabilidades
- Credenciales de solo lectura para la app de monitoreo
- El SDK no incluye herramientas de visualización embebidas

---

## 2. Responsabilidades

| Función | Descripción |
|---|---|
| Consulta de logs | Leer `app_logs` y `app_metrics` desde Supabase |
| Filtros en tiempo real | Plataforma, nivel, tag, session_id, rango de fechas |
| Visualización de crashes | Vista dedicada para eventos CRITICAL con stack traces |
| Métricas de performance | Gráficos de app_metrics (buffer_time, api_response_time) |
| Exportación | Exportar sesiones a JSON/CSV para issue tracker |
| Notificaciones | Alertas de eventos CRITICAL o ERROR |

---

## 3. Acceso a Datos

La app de monitoreo usa credenciales de solo lectura:

```sql
-- RLS para app de monitoreo (service_role)
CREATE POLICY monitor_read_policy ON app_logs
    FOR SELECT
    USING (true);

CREATE POLICY monitor_read_metrics ON app_metrics
    FOR SELECT
    USING (true);
```

---

## 4. Flujo del Beta Tester

1. Beta tester usa la app cliente (SDK integrado)
2. SDK captura errores, crashes y métricas automáticamente
3. Datos van a Supabase (o SQLite offline si no hay red)
4. QA abre la app de monitoreo para ver datos en tiempo real
5. Se filtra por session_id del beta tester específico
6. Se exporta reporte y se adjunta al issue tracker

---

## 5. Consultas Frecuentes

### Ver logs de una sesión específica

```sql
SELECT * FROM app_logs
WHERE session_id = 'uuid-del-beta-tester'
ORDER BY created_at DESC;
```

### Ver solo crashes

```sql
SELECT * FROM app_logs
WHERE level IN ('ERROR', 'CRITICAL')
ORDER BY created_at DESC;
```

### Métricas de performance por versión

```sql
SELECT name, AVG(value) as avg_value, unit
FROM app_metrics
WHERE tags->>'app_version' = '2.1.0'
GROUP BY name, unit;
```
