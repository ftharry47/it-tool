const db = require('../db');
const utils = require('../utils');

function generateCIId(data) {
  const d = data || db.readDb();
  let highest = 0;
  (d.cmdb || []).forEach(ci => {
    const id = String(ci.id || '');
    const match = id.match(/CI(\d+)/);
    if (match) {
      const n = parseInt(match[1], 10);
      if (!isNaN(n) && n > highest) highest = n;
    }
  });
  return 'CI' + String(highest + 1).padStart(7, '0');
}

function getCMDBItems(query) {
  const d = db.readDb();
  let items = Array.isArray(d.cmdb) ? d.cmdb : [];
  if (query) {
    const q = String(query).toLowerCase().trim();
    items = items.filter(ci =>
      (ci.name || '').toLowerCase().includes(q) ||
      (ci.id || '').toLowerCase().includes(q) ||
      (ci.type || '').toLowerCase().includes(q) ||
      (ci.owner || '').toLowerCase().includes(q)
    );
  }
  return { success: true, count: items.length, items };
}

function getCMDBItemById(id) {
  if (!id) return { success: false, error: 'CI ID is required' };
  const d = db.readDb();
  const ci = (d.cmdb || []).find(c => c.id === id);
  if (!ci) return { success: false, error: 'Configuration item not found' };
  return { success: true, item: ci };
}

function createCMDBItem(data) {
  if (!data || !data.name || !data.type) return { success: false, error: 'Name and type are required' };
  return db.withDb(d => {
    if (!Array.isArray(d.cmdb)) d.cmdb = [];
    const nextId = generateCIId(d);
    const ci = {
      id: data.id || nextId,
      name: String(data.name).trim(),
      type: String(data.type).trim(),
      owner: String(data.owner || '').trim(),
      status: data.status || 'Operational',
      configuration: data.configuration || {},
      location: String(data.location || '').trim(),
      purchaseDate: data.purchaseDate || '',
      warrantyExpiry: data.warrantyExpiry || '',
      linkedTickets: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    d.cmdb.push(ci);
    return { success: true, item: ci };
  });
}

function updateCMDBItem(id, data) {
  if (!id) return { success: false, error: 'CI ID is required' };
  return db.withDb(d => {
    const ci = (d.cmdb || []).find(c => c.id === id);
    if (!ci) return { success: false, error: 'Configuration item not found' };
    if (data.name) ci.name = String(data.name).trim();
    if (data.type) ci.type = String(data.type).trim();
    if (data.owner !== undefined) ci.owner = String(data.owner).trim();
    if (data.status) ci.status = data.status;
    if (data.configuration) ci.configuration = { ...ci.configuration, ...data.configuration };
    if (data.location !== undefined) ci.location = String(data.location).trim();
    if (data.purchaseDate !== undefined) ci.purchaseDate = data.purchaseDate;
    if (data.warrantyExpiry !== undefined) ci.warrantyExpiry = data.warrantyExpiry;
    ci.updatedAt = new Date().toISOString();
    return { success: true, item: ci };
  });
}

function deleteCMDBItem(id) {
  if (!id) return { success: false, error: 'CI ID is required' };
  return db.withDb(d => {
    const idx = (d.cmdb || []).findIndex(c => c.id === id);
    if (idx === -1) return { success: false, error: 'Configuration item not found' };
    d.cmdb.splice(idx, 1);
    (d.tickets || []).forEach(t => {
      if (Array.isArray(t['Configuration Items'])) {
        t['Configuration Items'] = t['Configuration Items'].filter(cid => cid !== id);
      }
    });
    return { success: true, message: 'Configuration item deleted' };
  });
}

function linkCMDBItemToTicket(ticketId, ciId, linkedBy) {
  if (!ticketId || !ciId) return { success: false, error: 'Ticket ID and CI ID are required' };
  return db.withDb(d => {
    const t = utils.findTicket(d, ticketId);
    if (!t) return { success: false, error: 'Ticket not found' };
    const ci = (d.cmdb || []).find(c => c.id === ciId);
    if (!ci) return { success: false, error: 'Configuration item not found' };
    if (!Array.isArray(t['Configuration Items'])) t['Configuration Items'] = [];
    if (!t['Configuration Items'].includes(ciId)) t['Configuration Items'].push(ciId);
    if (!Array.isArray(ci.linkedTickets)) ci.linkedTickets = [];
    if (!ci.linkedTickets.includes(ticketId)) ci.linkedTickets.push(ticketId);
    t['Last Updated'] = new Date().toISOString();
    return { success: true, ticketId, ciId, message: `Linked ${ciId} to ${ticketId}` };
  });
}

function unlinkCMDBItemFromTicket(ticketId, ciId) {
  if (!ticketId || !ciId) return { success: false, error: 'Ticket ID and CI ID are required' };
  return db.withDb(d => {
    const t = utils.findTicket(d, ticketId);
    if (!t) return { success: false, error: 'Ticket not found' };
    const ci = (d.cmdb || []).find(c => c.id === ciId);
    if (t['Configuration Items']) t['Configuration Items'] = t['Configuration Items'].filter(cid => cid !== ciId);
    if (ci && ci.linkedTickets) ci.linkedTickets = ci.linkedTickets.filter(id => id !== ticketId);
    t['Last Updated'] = new Date().toISOString();
    return { success: true, ticketId, ciId, message: `Unlinked ${ciId} from ${ticketId}` };
  });
}

function getTicketCIs(ticketId) {
  if (!ticketId) return { success: false, error: 'Ticket ID is required' };
  const d = db.readDb();
  const t = utils.findTicket(d, ticketId);
  if (!t) return { success: false, error: 'Ticket not found' };
  const ids = Array.isArray(t['Configuration Items']) ? t['Configuration Items'] : [];
  const items = (d.cmdb || []).filter(c => ids.includes(c.id));
  return { success: true, ticketId, items };
}

function getCMDBTypes() {
  const d = db.readDb();
  const types = d.cmdbTypes || ['Workstation', 'Server', 'Network', 'Application', 'Service', 'Peripheral'];
  return { success: true, types };
}

module.exports = {
  getCMDBItems,
  getCMDBItemById,
  createCMDBItem,
  updateCMDBItem,
  deleteCMDBItem,
  linkCMDBItemToTicket,
  unlinkCMDBItemFromTicket,
  getTicketCIs,
  getCMDBTypes
};
