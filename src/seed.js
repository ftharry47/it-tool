const db = require('./db');

const ADMIN_USERS = [
  {
    email: 'rick.barlow@alignedcardio.com',
    employeeId: 'rbarlow',
    displayName: 'Rick Barlow',
    password: 'Rbarlow89',
    role: 'Admin',
    supportLevel: 'L3',
    status: 'Online'
  },
  {
    email: 'srihari.thangavel@alignedcardio.com',
    employeeId: 'Shari47',
    displayName: 'SriHari Thangavel',
    password: 'Shari47',
    role: 'L2',
    supportLevel: 'L2',
    status: 'Online'
  },
  {
    email: 'monapuri.pranay@alignedcardio.com',
    employeeId: 'Mpranay55',
    displayName: 'Monapuri Pranay',
    password: 'Mpranay55',
    role: 'L2',
    supportLevel: 'L2',
    status: 'Online'
  }
];

function seedAdminUsers() {
  try {
    db.withDb(d => {
      if (!Array.isArray(d.users)) d.users = [];
      if (!Array.isArray(d.itStaff)) d.itStaff = [];

      ADMIN_USERS.forEach(u => {
        const email = u.email.toLowerCase().trim();
        const empId = String(u.employeeId).toLowerCase().trim();
        let user = d.users.find(x =>
          (x.email && x.email.toLowerCase().trim() === email) ||
          (x.employeeId && String(x.employeeId).toLowerCase().trim() === empId)
        );
        if (!user) {
          user = { email: u.email, employeeId: u.employeeId, password: u.password };
          d.users.push(user);
        }
        user.displayName = u.displayName;
        user.employeeId = u.employeeId;
        user.role = u.role;
        user.supportLevel = u.supportLevel;
        user.status = u.status;

        const level = user.supportLevel || user.role;
        if (['L1', 'L2', 'L3'].includes(level)) {
          const staff = d.itStaff.find(s =>
            String(s.name || '').toLowerCase() === user.displayName.toLowerCase() ||
            (s.email && s.email.toLowerCase() === email)
          );
          if (staff) {
            staff.name = user.displayName;
            staff.email = user.email;
            staff.level = level;
            staff.status = user.status;
          } else {
            d.itStaff.push({
              name: user.displayName,
              email: user.email,
              level,
              status: user.status
            });
          }
        }
      });
    });
    console.log('Admin users seeded successfully');
  } catch (e) {
    console.error('Seed admin users error:', e.message);
  }
}

module.exports = { seedAdminUsers };
