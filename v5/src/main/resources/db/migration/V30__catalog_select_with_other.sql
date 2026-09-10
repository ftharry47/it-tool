-- Convert the primary "what do you need" field on seeded catalog items to select_with_other
-- with a starter options list per category. Admins can refine via the options editor.

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["EHR (eClinicalWorks)","Imaging / PACS","ePrescribing","Lab interface","Reporting / analytics","Patient portal"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Clinical Software'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["Workstation","Monitor","Ultrasound / imaging device","EKG / diagnostic device","Label / document scanner","Peripheral (keyboard, mouse, etc.)"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Clinical Workstation / Device'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["No connectivity at site","Slow / intermittent connection","New network drop / port","Wi-Fi coverage issue","VPN access","Firewall / port change"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Clinic Network / Connectivity'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["New mailbox","Shared mailbox access","Distribution list change","Mailbox permissions","Email delivery issue","Signature / alias change"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Email & Communication'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["EHR access","Imaging / PACS access","Shared drive / folder access","VPN access","New user account","Role / permission change"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'System Access'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["Password reset","Account unlock","MFA / authenticator issue","New account setup"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Password Reset / Account Unlock'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["New printer setup","Toner / supplies","Label printer issue","Printer offline / not printing","Scan-to-email issue"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Printer / Label Printer'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["New mobile device","BYOD enrollment","Mobile plan change","Mobile app support","Device repair / replacement"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Mobile Device / BYOD'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["New article request","Update existing article","Clinical workflow documentation","IT how-to guide"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'Knowledge Base'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;

UPDATE catalog_item
SET form_schema = jsonb_set(
        form_schema,
        '{0}',
        (form_schema -> 0)
            || '{"type":"select_with_other"}'::jsonb
            || jsonb_build_object('options', '["General IT question","Report an issue","Request a callback","Feedback"]'::jsonb),
        true
    ),
    updated_at = now()
WHERE name = 'General IT Help Desk'
  AND jsonb_typeof(form_schema) = 'array'
  AND jsonb_array_length(form_schema) > 0;
