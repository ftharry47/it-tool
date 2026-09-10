-- Make catalog item names and descriptions explicitly about NEW/REPLACEMENT
-- requests vs broken-equipment Incidents, to reduce end-user confusion.

UPDATE catalog_item
SET name = 'New / Replacement Clinical Software',
    description = 'Request access to or a license for clinical software such as EHR, PACS imaging, ePrescribing, lab interfaces, or reporting tools. For software that is broken or not working, report an Incident instead.',
    category = 'New / Replacement Clinical Software'
WHERE name = 'Clinical Software';

UPDATE catalog_item
SET name = 'New / Replacement Workstation or Device',
    description = 'Request a NEW workstation, monitor, printer, clinical peripheral, or diagnostic device, or a replacement for equipment that is broken beyond repair. If your current device just needs troubleshooting, report an Incident instead.',
    category = 'New / Replacement Workstation or Device'
WHERE name = 'Clinical Workstation / Device';

UPDATE catalog_item
SET name = 'Network Access / Connectivity Request',
    description = 'Request a new network drop, Wi-Fi coverage change, VPN access, or firewall/port change. If the network is down, slow, or intermittent, report an Incident instead.',
    category = 'Network Access / Connectivity Request'
WHERE name = 'Clinic Network / Connectivity';

UPDATE catalog_item
SET name = 'Email / Communication Request',
    description = 'Request a new mailbox, shared mailbox access, distribution list, or email permissions. For email that is not working, report an Incident instead.',
    category = 'Email / Communication Request'
WHERE name = 'Email & Communication';

UPDATE catalog_item
SET name = 'System / Application Access Request',
    description = 'Request access to clinical or practice systems, shared drives, or applications with business justification. For access that suddenly stopped working, report an Incident instead.',
    category = 'System / Application Access Request'
WHERE name = 'System Access';

UPDATE catalog_item
SET name = 'Password Reset / Account Unlock',
    description = 'Request a password reset or account unlock.',
    category = 'Password Reset / Account Unlock'
WHERE name = 'Password Reset / Account Unlock';

UPDATE catalog_item
SET name = 'New Printer / Toner / Label Printer Setup',
    description = 'Request a new printer, label printer, toner/supplies, or setup. If your current printer is broken, offline, or not printing, report an Incident instead.',
    category = 'New Printer / Toner / Label Printer Setup'
WHERE name = 'Printer / Label Printer';

UPDATE catalog_item
SET name = 'New Mobile Device / BYOD Request',
    description = 'Request a new mobile device, BYOD enrollment, plan change, or app support. For a broken or lost device, report an Incident instead.',
    category = 'New Mobile Device / BYOD Request'
WHERE name = 'Mobile Device / BYOD';

UPDATE catalog_item
SET name = 'New / Updated Knowledge Base Article',
    description = 'Request a new knowledge base article or update an existing one.',
    category = 'New / Updated Knowledge Base Article'
WHERE name = 'Knowledge Base';

UPDATE catalog_item
SET name = 'General IT Request / Question',
    description = 'General IT support request or question for the practice.',
    category = 'General IT Request / Question'
WHERE name = 'General IT Help Desk';
