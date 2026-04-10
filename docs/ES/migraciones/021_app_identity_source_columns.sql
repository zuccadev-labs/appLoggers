-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration 021: Promote app identity and source context to top-level columns
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE public.app_logs
    ADD COLUMN IF NOT EXISTS app_package TEXT,
    ADD COLUMN IF NOT EXISTS source_scope TEXT,
    ADD COLUMN IF NOT EXISTS source_file TEXT,
    ADD COLUMN IF NOT EXISTS source_method TEXT;

ALTER TABLE public.app_metrics
    ADD COLUMN IF NOT EXISTS app_package TEXT,
    ADD COLUMN IF NOT EXISTS source_scope TEXT,
    ADD COLUMN IF NOT EXISTS source_file TEXT,
    ADD COLUMN IF NOT EXISTS source_method TEXT;

CREATE INDEX IF NOT EXISTS idx_app_logs_app_package ON public.app_logs (app_package);
CREATE INDEX IF NOT EXISTS idx_app_logs_source_scope ON public.app_logs (source_scope);
CREATE INDEX IF NOT EXISTS idx_app_metrics_app_package ON public.app_metrics (app_package);
CREATE INDEX IF NOT EXISTS idx_app_metrics_source_scope ON public.app_metrics (source_scope);

COMMENT ON COLUMN public.app_logs.app_package IS 'Identidad estable de la app que emitió el evento (ej: com.methosmedia.klinema).';
COMMENT ON COLUMN public.app_logs.source_scope IS 'Origen jerárquico estable del evento para filtros corporativos.';
COMMENT ON COLUMN public.app_logs.source_file IS 'Archivo fuente opcional capturado por modo forense de caller capture.';
COMMENT ON COLUMN public.app_logs.source_method IS 'Método o función opcional capturado por modo forense de caller capture.';
COMMENT ON COLUMN public.app_metrics.app_package IS 'Identidad estable de la app que emitió la métrica.';
COMMENT ON COLUMN public.app_metrics.source_scope IS 'Origen jerárquico estable de la métrica para filtros corporativos.';
COMMENT ON COLUMN public.app_metrics.source_file IS 'Archivo fuente opcional capturado por modo forense de caller capture.';
COMMENT ON COLUMN public.app_metrics.source_method IS 'Método o función opcional capturado por modo forense de caller capture.';

UPDATE public.app_logs
SET app_package = COALESCE(app_package, extra->>'app_package'),
    source_scope = COALESCE(source_scope, extra->>'source_scope', tag),
    source_file = COALESCE(source_file, extra->>'source_file'),
    source_method = COALESCE(source_method, extra->>'source_method')
WHERE app_package IS NULL
   OR source_scope IS NULL
   OR source_file IS NULL
   OR source_method IS NULL;

UPDATE public.app_metrics
SET source_scope = COALESCE(source_scope, tags->>'source_scope', tags->>'source')
WHERE source_scope IS NULL;

CREATE OR REPLACE FUNCTION public.correlate_beta_tester_email()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    is_beta BOOLEAN;
    has_email BOOLEAN;
    tester_email TEXT;
    lookup_email TEXT;
BEGIN
    is_beta := (NEW.extra->>'is_beta_tester') = 'true';
    IF NOT is_beta THEN
        RETURN NEW;
    END IF;

    tester_email := NEW.extra->>'beta_tester_email';
    has_email := tester_email IS NOT NULL AND tester_email != '';

    IF has_email THEN
        INSERT INTO beta_tester_devices (device_id, email, app_package, updated_at)
        VALUES (
            NEW.device_id,
            tester_email,
            COALESCE(NEW.app_package, NEW.extra->>'app_package'),
            NOW()
        )
        ON CONFLICT (device_id) DO UPDATE
        SET email = EXCLUDED.email,
            app_package = EXCLUDED.app_package,
            updated_at = NOW();
    ELSE
        SELECT email INTO lookup_email
        FROM beta_tester_devices
        WHERE device_id = NEW.device_id;

        IF lookup_email IS NOT NULL THEN
            NEW.extra := jsonb_set(
                COALESCE(NEW.extra, '{}'::jsonb),
                '{beta_tester_email}',
                to_jsonb(lookup_email)
            );
        END IF;
    END IF;

    RETURN NEW;
END;
$$;