-- Update incident categories with cardiology-practice-relevant descriptions
UPDATE category
SET description = 'EHR, imaging, or clinical software issue'
WHERE name = 'Software';

UPDATE category
SET description = 'Workstation, monitor, or device issue'
WHERE name = 'Hardware';

UPDATE category
SET description = 'Clinic network or connectivity issue'
WHERE name = 'Network';

UPDATE category
SET description = 'Email or communication issue'
WHERE name = 'Email';

UPDATE category
SET description = 'System access request'
WHERE name = 'Access';

UPDATE category
SET description = 'Password reset request'
WHERE name = 'Password';

UPDATE category
SET description = 'Printer or label printer issue'
WHERE name = 'Printer';

UPDATE category
SET description = 'Mobile device or BYOD issue'
WHERE name = 'Mobile';

UPDATE category
SET description = 'Knowledge base request'
WHERE name = 'Knowledge Base';

UPDATE category
SET description = 'General service desk request'
WHERE name = 'Service Desk';

-- Update service catalog items with clinical/cardiologist-facing names and descriptions
UPDATE catalog_item
SET name = 'Clinical Software',
    description = 'Request EHR, imaging/PACS, or other clinical software access or licenses.',
    category = 'Clinical Software'
WHERE name = 'Software';

UPDATE catalog_item
SET name = 'Clinical Workstation / Device',
    description = 'Request a workstation, monitor, or clinical peripheral for a site.',
    category = 'Clinical Workstation / Device'
WHERE name = 'Hardware';

UPDATE catalog_item
SET name = 'Clinic Network / Connectivity',
    description = 'Report network issues or request connectivity changes at a site.',
    category = 'Clinic Network / Connectivity'
WHERE name = 'Network';

UPDATE catalog_item
SET name = 'Email & Communication',
    description = 'Request email account changes, distribution lists, or mailbox issues.',
    category = 'Email & Communication'
WHERE name = 'Email';

UPDATE catalog_item
SET name = 'System Access',
    description = 'Request access to clinical or practice systems, with business justification.',
    category = 'System Access'
WHERE name = 'Access';

UPDATE catalog_item
SET name = 'Password Reset / Account Unlock',
    description = 'Request a password reset or account unlock.',
    category = 'Password Reset / Account Unlock'
WHERE name = 'Password';

UPDATE catalog_item
SET name = 'Printer / Label Printer',
    description = 'Request printer setup, toner, or label printer issues.',
    category = 'Printer / Label Printer'
WHERE name = 'Printer';

UPDATE catalog_item
SET name = 'Mobile Device / BYOD',
    description = 'Request mobile device, plan, or app support.',
    category = 'Mobile Device / BYOD'
WHERE name = 'Mobile';

UPDATE catalog_item
SET name = 'Knowledge Base',
    description = 'Request a new knowledge base article or update an existing one.',
    category = 'Knowledge Base'
WHERE name = 'Knowledge Base';

UPDATE catalog_item
SET name = 'General IT Help Desk',
    description = 'General IT support request for the practice.',
    category = 'General IT Help Desk'
WHERE name = 'Service Desk';
