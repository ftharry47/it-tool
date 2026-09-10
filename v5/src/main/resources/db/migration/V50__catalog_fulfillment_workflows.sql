-- Part F: add per-task fulfillment workflow + optional order details.

ALTER TABLE fulfillment_task
    ADD COLUMN workflow varchar(20) NOT NULL DEFAULT 'FULL',
    ADD COLUMN order_id varchar(100),
    ADD COLUMN vendor varchar(100);

-- Workflow assignments:
--   INSTANT  -> Password Reset / Account Unlock (PENDING -> COMPLETED directly)
--   SOFTWARE -> license/access-style items (PENDING -> ORDERED -> DELIVERY_DATE_SET -> DELIVERED -> COMPLETED,
--               but no physical order/shipping fields)
--   FULL     -> physical items (PENDING -> ORDERED -> DELIVERY_DATE_SET -> DELIVERED -> COMPLETED,
--               with optional orderId/vendor)

UPDATE catalog_item
SET fulfillment_tasks = jsonb_set(
    COALESCE(fulfillment_tasks, '[]'::jsonb),
    '{0,workflow}',
    to_jsonb('INSTANT'::text),
    true
)
WHERE name = 'Password Reset / Account Unlock';

UPDATE catalog_item
SET fulfillment_tasks = jsonb_set(
    COALESCE(fulfillment_tasks, '[]'::jsonb),
    '{0,workflow}',
    to_jsonb('SOFTWARE'::text),
    true
)
WHERE name IN (
    'Clinical Software',
    'Email & Communication',
    'System Access',
    'Knowledge Base',
    'General IT Help Desk'
);

UPDATE catalog_item
SET fulfillment_tasks = jsonb_set(
    COALESCE(fulfillment_tasks, '[]'::jsonb),
    '{0,workflow}',
    to_jsonb('FULL'::text),
    true
)
WHERE name IN (
    'Clinical Workstation / Device',
    'Printer / Label Printer',
    'Mobile Device / BYOD',
    'Clinic Network / Connectivity'
);
